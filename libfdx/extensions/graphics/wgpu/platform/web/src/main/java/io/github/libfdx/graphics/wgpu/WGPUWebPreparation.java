package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUCreatePipelineAsyncStatus;
import com.github.xpenatan.webgpu.WGPUCreateRenderPipelineAsyncCallback;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
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
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.teavm.jso.browser.Window;

/** Loading-only Java source/reflection work followed by the browser's async GPU pipeline creation. */
final class WGPUWebPreparation extends WGPUPreparation {
    private ShaderPreparationCapabilities capabilities;
    private final ArrayList<Job> jobs = new ArrayList<>();
    private WGPUContext context;
    private WGPUResourceDomain domain;
    private ShaderCompilerRegistry compilers;
    private ShaderCompilerRegistry refreshCompilers;
    private ShaderArtifactCache cache;
    private boolean closed;

    @Override void initialize(WGPUContext context, int workers) {
        this.context = context;
        domain = context.resourceDomain();
        cache = context.configuration().shaderCache();
        capabilities = new ShaderPreparationCapabilities(ShaderPreparationCapabilities.Execution.OWNER_THREAD,
                ShaderPreparationCapabilities.Execution.NATIVE_ASYNC, false, 0,
                cache != null && cache.enabled(), false);
    }

    @Override ShaderPreparationCapabilities capabilities() { return capabilities; }

    @Override ShaderPreparationOperation submit(ShaderPipelineRequest request) {
        context.requireDeviceUsable("prepare a browser render pipeline");
        if (closed) throw new FdxException("WebGPU preparation is closed");
        domain.retainPreparation();
        Job job = new Job(request);
        jobs.add(job);
        return job;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Job job : new ArrayList<>(jobs)) job.cancel();
    }

    private final class Job implements ShaderPreparationOperation {
        private final ShaderPipelineRequest request;
        private final ShaderPreparationTrace trace = new ShaderPreparationTrace();
        private WGPUShaderModuleHandle module;
        private WGPUGraphicsDevice.RenderPipelineInputs inputs;
        private WGPUCreateRenderPipelineAsyncCallback callback;
        private WGPURenderPipelineHandle output;
        private Throwable failure;
        private boolean started, done, cancelled, finished, disposed, released;
        private boolean submitted, refreshed, cacheEligible;
        private final ArrayDeque<Runnable> loadingWork = new ArrayDeque<>();

        Job(ShaderPipelineRequest request) { this.request = request; }

        @Override public void advanceLoading() {
            if (done || cancelled) return;
            if (!started) {
                started = true;
                prepareSource();
            }
            // Storage completions queue continuations; ordinary polling never runs this work.
            long startedAt = System.nanoTime();
            for (int step = 0; step < 32 && !done && !loadingWork.isEmpty(); step++) {
                loadingWork.remove().run();
                // Soft budget: an individual source callback/native submission cannot be preempted.
                if (System.nanoTime() - startedAt >= 2_000_000L) break;
            }
        }

        private void prepareSource() {
            Consumer<Runnable> execute = trace.executor(work -> { checkCancelled(); loadingWork.add(work); });
            FdxFuture<ShaderModuleDescriptor> generated = ShaderCompilationTasks.then(
                    ShaderCompilationTasks.submit(execute, () -> {
                        checkCancelled();
                        trace.enter(ShaderPreparationPhase.SOURCE);
                        return request.sourceDescriptorAsync(execute);
                    }), execute, pending -> pending);
            FdxFuture<ShaderModuleDescriptor> sourceReady = ShaderCompilationTasks.then(generated, execute, source -> {
                        checkCancelled();
                        trace.enter(ShaderPreparationPhase.TRANSLATION);
                        cacheEligible = source.targetArtifact() == null && cache != null && cache.enabled();
                        if (source.targetArtifact() != null) {
                            ShaderTargetSupport.forProvider(WGPUProvider.ID).require(source.targetArtifact());
                            return FdxFuture.completed(source);
                        }
                        if (compilers == null) {
                            var compiler = RuntimeCore.shaderCompiler();
                            compilers = ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                                            RuntimeShaderTargetCompiler.VERSION, cache))
                                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
                            refreshCompilers = cache == null || !cache.enabled() ? compilers
                                    : ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                                            RuntimeShaderTargetCompiler.VERSION, cache.refreshing()))
                                            .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
                        }
                        return ShaderModuleDescriptors.requireTargetAsync(source, ShaderTarget.WGPU_WGSL,
                                refreshed ? refreshCompilers : compilers, ShaderVerificationRequirement.REQUIRED,
                                "WebGPU", execute);
                    });
            ShaderCompilationTasks.then(sourceReady, execute, source -> {
                submitNative(source);
                return FdxFuture.completed(null);
            }).onFailure(error -> {
                if (!done) { failure = error; complete(null, null, null); }
            });
        }

        private void submitNative(ShaderModuleDescriptor source) {
            try {
                checkCancelled();
                context.requireDeviceUsable("prepare a browser pipeline during loading");
                if (!source.reflection().complete()) throw new FdxException("WebGPU preparation requires complete reflection");
                checkCancelled();
                submitted = true;
                trace.enter(ShaderPreparationPhase.COMPILATION);
                module = WGPUGraphicsDevice.createNativeShader(context.nativeDevice(), domain, null, source);
                inputs = WGPUGraphicsDevice.createRenderPipelineInputs(context.nativeDevice(), domain, null,
                        request.pipelineDescriptor(module), module);
                callback = new WGPUCreateRenderPipelineAsyncCallback() {
                    @Override protected void onCallback(WGPUCreatePipelineAsyncStatus status,
                            WGPURenderPipeline pipeline, String message) {
                        // Retire the native callback only after its C++ virtual call has returned.
                        Window.setTimeout(() -> complete(status, pipeline, message), 0);
                    }
                };
                trace.enter(ShaderPreparationPhase.PIPELINE);
                context.nativeDevice().createRenderPipelineAsync(inputs.descriptor,
                        WGPUCallbackMode.AllowSpontaneous, callback);
            } catch (Throwable error) {
                failure = error;
                complete(null, null, null);
            }
        }

        private void complete(WGPUCreatePipelineAsyncStatus status, WGPURenderPipeline pipeline, String message) {
            try {
                if (failure == null && !cancelled && !closed && !domain.isClosed()) {
                    if (status != WGPUCreatePipelineAsyncStatus.Success || pipeline == null || !pipeline.isValid()) {
                        throw new FdxException("WebGPU async pipeline failed (" + status + "): " + message);
                    }
                    output = inputs.takePipeline(pipeline);
                    pipeline = null;
                }
            } catch (Throwable error) { failure = error; }
            WGPUCleanup cleanup = new WGPUCleanup();
            WGPURenderPipeline unused = pipeline;
            if (unused != null) {
                cleanup.run(() -> { if (unused.isValid()) unused.release(); });
                cleanup.run(unused::dispose);
            }
            if (callback != null) cleanup.run(callback::dispose);
            if (inputs != null) cleanup.run(inputs::close);
            if (module != null) cleanup.run(module::dispose);
            callback = null;
            inputs = null;
            module = null;
            try { cleanup.throwIfFailed(); }
            catch (Throwable error) { failure = WGPUCleanup.merge(failure, error); }
            boolean retry = submitted && failure instanceof RuntimeException && output == null
                    && cacheEligible && !refreshed && !cancelled && !closed && !domain.isClosed();
            submitted = false;
            if (retry) {
                refreshed = true;
                started = false;
                failure = null;
                trace.enter(ShaderPreparationPhase.QUEUED);
                return; // Only a later explicit loading update may rebuild rejected artifacts.
            }
            trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT);
            done = true;
            if (failure != null || cancelled || closed || domain.isClosed()) discard();
        }

        private void checkCancelled() {
            if (cancelled || closed || domain.isClosed()) throw new CancellationException("WebGPU preparation cancelled");
        }

        private void release() {
            if (released) return;
            released = true;
            jobs.remove(this);
            domain.releasePreparation();
        }

        private void discard() {
            WGPURenderPipelineHandle unused = output;
            output = null;
            try { if (unused != null) unused.dispose(); }
            finally { release(); }
        }

        @Override public boolean isDone() { return done; }
        @Override public ShaderPreparationPhase phase() { return trace.phase(); }
        @Override public ShaderPreparationTrace trace() { return trace; }

        @Override public ShaderPreparedResult finish() {
            if (!done || finished || disposed) throw new FdxException("WebGPU preparation cannot be published now");
            finished = true;
            try {
                checkCancelled();
                context.requireDeviceUsable("publish a browser pipeline");
                if (failure instanceof Error error) throw error;
                if (failure instanceof RuntimeException error) throw error;
                if (failure != null) throw new FdxException("WebGPU preparation failed", failure);
                if (output == null) throw new FdxException("WebGPU preparation has no pipeline");
                ShaderPreparedResult result = new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(),
                        output, output.resourceBindings().layout(), request.providerRevision()), output);
                output.publish();
                output = null;
                return result;
            } finally { discard(); }
        }

        @Override public void cancel() {
            cancelled = true;
            loadingWork.clear();
            if (!submitted) done = true;
            if (done) discard();
        }

        @Override public void dispose() {
            if (disposed) return;
            if (!done) throw new FdxException("WebGPU preparation callback has not drained");
            disposed = true;
            discard();
        }

        @Override public boolean isDisposed() { return disposed; }
    }
}
