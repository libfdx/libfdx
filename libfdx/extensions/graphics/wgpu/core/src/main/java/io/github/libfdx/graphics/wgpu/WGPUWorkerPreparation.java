package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUDevice;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationTrace;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.RuntimeShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.RuntimeWgslTargetVerifier;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import io.github.libfdx.runtime.core.RuntimeCore;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Bounded native preparation, enabled only by an audited binding's service registration. */
abstract class WGPUWorkerPreparation extends WGPUPreparation {
    private WGPUContext context;
    private WGPUResourceDomain domain;
    private WGPUDevice device;
    private WGPUCreationErrors errors;
    private Thread owner;
    private ShaderPreparationCapabilities capabilities;
    private ShaderCompilerRegistry compilers;
    private ShaderCompilerRegistry refreshCompilers;
    private ShaderArtifactCache cache;
    private ThreadPoolExecutor executor;
    private boolean nativeOnOwner;
    private WGPUDawnPipelines dawn;
    private final Set<Job> jobs = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    @Override
    void initialize(WGPUContext context, int workers) {
        initialize(context, workers, false);
    }

    /** GLES native creation holds the rendering context lock, so only explicit loading may advance it. */
    final void initialize(WGPUContext context, int workers, boolean nativeOnOwner) {
        this.context = context;
        this.nativeOnOwner = nativeOnOwner;
        domain = context.resourceDomain();
        device = context.nativeDevice();
        errors = context.creationErrors();
        owner = Thread.currentThread();
        cache = context.configuration().shaderCache();
        if (context.configuration().loaderBackend() == WGPULoaderBackend.DAWN) {
            if (!WGPU.isDawnBackend()) throw new FdxException("Dawn preparation requires the Dawn native library");
            dawn = new WGPUDawnPipelines(context);
        }
        capabilities = new ShaderPreparationCapabilities(ShaderPreparationCapabilities.Execution.WORKERS,
                dawn != null ? ShaderPreparationCapabilities.Execution.NATIVE_ASYNC
                        : nativeOnOwner ? ShaderPreparationCapabilities.Execution.OWNER_THREAD
                        : ShaderPreparationCapabilities.Execution.WORKERS, !nativeOnOwner, workers,
                cache != null && cache.enabled(), false);
    }

    @Override
    ShaderPreparationCapabilities capabilities() { return capabilities; }

    @Override
    ShaderPreparationOperation submit(ShaderPipelineRequest request) {
        requireOwner();
        context.requireDeviceUsable("prepare a render pipeline");
        if (closed) throw new FdxException("WGPU preparation is closed");
        if (dawn != null && jobs.size() >= 256 + capabilities.workerLimit())
            throw new RejectedExecutionException("Dawn preparation queue is full");
        if (executor == null) {
            var compiler = RuntimeCore.shaderCompiler();
            compilers = ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, cache))
                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            refreshCompilers = cache == null || !cache.enabled() ? compilers
                    : ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, cache.refreshing()))
                            .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            int workers = capabilities.workerLimit();
            executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(256), work -> {
                        Thread thread = new Thread(work, "libfdx-wgpu-prepare");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
        }
        domain.retainPreparation();
        Job job = new Job(request);
        jobs.add(job);
        try { job.prepare(); }
        catch (RuntimeException | Error failure) {
            jobs.remove(job);
            job.releaseDomain();
            throw failure;
        }
        return job;
    }

    @Override
    public void close() {
        requireOwner();
        if (closed) return;
        closed = true;
        if (dawn != null) dawn.close();
        for (Job job : jobs) job.cancel();
        // Queued cancellations release their retained domain on the short worker path. Never join here.
        if (executor != null) executor.shutdown();
        drainDawn();
    }

    private void drainDawn() {
        if (dawn != null && closed && jobs.isEmpty()) dawn.drained();
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new FdxException("WGPU preparation publication requires the owner thread");
    }

    private final class Job implements ShaderPreparationOperation {
        private final ShaderPipelineRequest request;
        private final AtomicReference<WGPURenderPipelineHandle> output = new AtomicReference<>();
        private final AtomicBoolean domainReleased = new AtomicBoolean();
        private final AtomicBoolean retirementClaimed = new AtomicBoolean();
        private final ShaderPreparationTrace trace = new ShaderPreparationTrace();
        private volatile boolean cancelled, done;
        private Throwable failure;
        private boolean finished, disposed;
        // Only the short CPU-to-owner handoff is synchronized; compilation never holds this monitor.
        private ShaderModuleDescriptor loadingSource;
        private boolean awaitingLoading;
        private boolean refreshed, cacheEligible;

        Job(ShaderPipelineRequest request) { this.request = request; }

        private void prepare() {
            Consumer<Runnable> execute = trace.executor(executor::execute);
            FdxFuture<ShaderModuleDescriptor> sourceReady = ShaderCompilationTasks.then(ShaderCompilationTasks.submit(execute, () -> {
                checkCancelled();
                trace.enter(ShaderPreparationPhase.SOURCE);
                return request.sourceDescriptor();
            }), execute, source -> {
                checkCancelled();
                trace.enter(ShaderPreparationPhase.TRANSLATION);
                cacheEligible = source.targetArtifact() == null && refreshCompilers != compilers;
                if (source.targetArtifact() != null) {
                    ShaderTargetSupport.forProvider(WGPUProvider.ID).require(source.targetArtifact());
                    return FdxFuture.completed(source);
                }
                return ShaderModuleDescriptors.requireTargetAsync(source, ShaderTarget.WGPU_WGSL,
                        refreshed ? refreshCompilers : compilers, ShaderVerificationRequirement.REQUIRED, "WGPU", execute);
            });
            ShaderCompilationTasks.then(sourceReady, execute, translated -> {
                // Explicit dispatch also handles an already-completed source/cache future.
                boolean handedOff = false;
                try {
                    if (!translated.reflection().complete()) {
                        throw new FdxException("WGPU prepared pipelines require complete shader reflection");
                    }
                    checkCancelled();
                    if (nativeOnOwner) {
                        synchronized (this) {
                            checkCancelled();
                            loadingSource = translated;
                            awaitingLoading = true;
                            handedOff = true;
                            trace.enter(ShaderPreparationPhase.COMPILATION);
                        }
                    } else {
                        try { handedOff = compileNative(translated); }
                        catch (RuntimeException error) {
                            if (!retry()) throw error;
                            handedOff = true;
                        }
                    }
                } catch (Throwable error) { failure = error; }
                finally { if (!handedOff) complete(); }
                return FdxFuture.completed(null);
            }).onFailure(error -> { failure = error; complete(); });
        }

        private boolean retry() {
            if (refreshed || !cacheEligible || cancelled || closed || domain.isClosed()) return false;
            refreshed = true;
            prepare();
            return true;
        }

        @Override
        public void advanceLoading() {
            requireOwner();
            if (!nativeOnOwner || done || disposed) return;
            ShaderModuleDescriptor translated;
            synchronized (this) {
                if (!awaitingLoading || cancelled || closed) return;
                translated = loadingSource;
                loadingSource = null;
                awaitingLoading = false;
            }
            boolean retried = false;
            try {
                checkCancelled();
                context.requireDeviceUsable("prepare a render pipeline during loading");
                compileNative(translated);
            } catch (RuntimeException error) {
                retried = retry();
                if (!retried) failure = error;
            } catch (Throwable error) { failure = error; }
            finally { if (!retried) complete(); }
        }

        private boolean compileNative(ShaderModuleDescriptor translated) {
            trace.enter(ShaderPreparationPhase.COMPILATION);
            WGPUShaderModuleHandle module = WGPUGraphicsDevice.createNativeShader(device, domain, errors, translated);
            boolean handedOff = false;
            try {
                checkCancelled();
                trace.enter(ShaderPreparationPhase.PIPELINE);
                if (dawn != null) {
                    WGPUGraphicsDevice.RenderPipelineInputs inputs = WGPUGraphicsDevice.createRenderPipelineInputs(
                            device, domain, errors, request.pipelineDescriptor(module), module);
                    FdxFuture<WGPURenderPipelineHandle> future;
                    try { future = dawn.submit(inputs, module); }
                    catch (RuntimeException | Error error) { inputs.close(); throw error; }
                    handedOff = true;
                    future.onSuccess(pipeline -> { output.set(pipeline); complete(); });
                    future.onFailure(error -> {
                        try { if (retry()) return; }
                        catch (Throwable retryFailure) { error.addSuppressed(retryFailure); }
                        failure = error;
                        complete();
                    });
                    return true;
                }
                output.set(WGPUGraphicsDevice.createNativePipeline(device, domain, errors,
                        request.pipelineDescriptor(module), module));
                return false;
            } finally { if (!handedOff) module.dispose(); }
        }

        private void complete() {
            try {
                if (failure != null || cancelled || closed) discard();
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            } finally {
                trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT);
                done = true;
                // Close/cancel may have raced the first check before done became visible.
                if (cancelled || closed) discard();
                if (output.get() == null) jobs.remove(this);
                drainDawn();
            }
        }

        private void checkCancelled() {
            if (cancelled || closed || domain.isClosed()) throw new CancellationException("WGPU preparation cancelled");
        }

        private void releaseDomain() {
            if (domainReleased.compareAndSet(false, true)) domain.releasePreparation();
        }

        private void discard() {
            if (!retirementClaimed.compareAndSet(false, true)) return;
            WGPURenderPipelineHandle pipeline = output.getAndSet(null);
            try { if (pipeline != null) pipeline.dispose(); }
            finally { releaseDomain(); }
        }

        @Override
        public boolean isDone() { return done; }
        @Override
        public ShaderPreparationPhase phase() { return trace.phase(); }
        @Override
        public ShaderPreparationTrace trace() { return trace; }

        @Override
        public ShaderPreparedResult finish() {
            requireOwner();
            if (!done || finished || disposed) throw new FdxException("WGPU preparation cannot be published now");
            finished = true;
            retirementClaimed.set(true);
            WGPURenderPipelineHandle pipeline = output.getAndSet(null);
            try {
                checkCancelled();
                context.requireDeviceUsable("publish a render pipeline");
                if (failure instanceof Error error) throw error;
                if (failure instanceof RuntimeException error) throw error;
                if (failure != null) throw new FdxException("WGPU preparation failed", failure);
                if (pipeline == null) throw new CancellationException("WGPU prepared result was retired");
                ShaderPreparedResult result = new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(),
                        pipeline, pipeline.resourceBindings().layout(), request.providerRevision()), pipeline);
                pipeline.publish();
                pipeline = null;
                return result;
            } finally {
                try { if (pipeline != null) pipeline.dispose(); }
                finally { releaseDomain(); jobs.remove(this); }
            }
        }

        @Override
        public void cancel() {
            requireOwner();
            boolean retireLoading;
            synchronized (this) {
                cancelled = true;
                retireLoading = awaitingLoading;
                awaitingLoading = false;
                loadingSource = null;
            }
            if (retireLoading) complete();
            if (done) { discard(); jobs.remove(this); }
        }

        @Override
        public void dispose() {
            requireOwner();
            if (disposed) return;
            if (!done) throw new FdxException("WGPU native preparation has not drained");
            disposed = true;
            discard();
            jobs.remove(this);
        }

        @Override
        public boolean isDisposed() { return disposed; }
    }
}
