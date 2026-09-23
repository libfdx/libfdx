package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.target.*;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent provider workers; context registries are accessed only in finish(), on the owner. */
final class D3D12PreparationQueue implements AutoCloseable {
    private final D3D12Context context;
    private final GraphicsCapabilities capabilities;
    private final ShaderCompilerRegistry compilers;
    private final ShaderCompilerRegistry refreshCompilers;
    private final D3D12PreparedStages stages;
    private final ShaderArtifactCache cache;
    private final String pipelineCacheIdentity;
    private final ThreadPoolExecutor executor;
    private final Set<Job> jobs = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    D3D12PreparationQueue(D3D12Context context, GraphicsCapabilities capabilities,
            ShaderCompilerRegistry compilers, ShaderCompilerRegistry refreshCompilers,
            int workers, boolean validation, boolean optimize, ShaderArtifactCache cache) {
        this.context = context;
        this.capabilities = capabilities;
        this.compilers = compilers;
        this.refreshCompilers = refreshCompilers;
        this.cache = cache;
        pipelineCacheIdentity = D3D12Native.pipelineCacheIdentity(context.nativeHandle());
        stages = new D3D12PreparedStages(validation, optimize, 256, cache);
        var compilerThreads = D3D12DxcCompiler.workerThreads();
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), work -> {
                    Thread thread = compilerThreads.newThread(work);
                    thread.setName("libfdx-d3d12-prepare-" + thread.threadId());
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    ShaderPreparationOperation submit(ShaderPipelineRequest request) {
        if (closed) throw new FdxException("D3D12 preparation is closed");
        context.requireUsable("prepare a render pipeline");
        Job job = new Job(request, D3D12Native.retainPreparationDevice(context.nativeHandle()));
        jobs.add(job);
        try { executor.execute(job.trace.wrap(job)); }
        catch (RuntimeException | Error failure) {
            jobs.remove(job);
            D3D12Ffm.release(job.device);
            throw failure;
        }
        return job;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Job job : jobs) job.cancel();
        // Queued jobs run their short cancellation path and release their retained device input.
        // shutdown() never joins. Each worker closes its DXC session on that same worker.
        executor.shutdown();
        stages.close();
    }

    private FdxFuture<ShaderModuleDescriptor> translateAsync(ShaderModuleDescriptor source, ShaderCompilerRegistry registry,
            Consumer<Runnable> execute) {
        if (source.targetArtifact() != null
                && source.targetArtifact().target().equals(ShaderTarget.DIRECTX_HLSL.id())) {
            ShaderTargetSupport.forProvider(D3D12Provider.ID).require(source.targetArtifact());
            return FdxFuture.completed(source);
        }
        FdxFuture<ShaderModuleDescriptor> reflected = FdxFuture.completed(source);
        if (!source.reflection().complete()) {
            reflected = ShaderModuleDescriptors.requireTargetAsync(source, ShaderTarget.WGPU_WGSL,
                    registry, ShaderVerificationRequirement.REQUIRED, "D3D12 reflection", execute);
        }
        return ShaderCompilationTasks.then(reflected, execute, descriptor ->
                ShaderModuleDescriptors.requireTargetAsync(descriptor.entryPoints(source.vertexEntryPoint(), source.fragmentEntryPoint()),
                        ShaderTarget.DIRECTX_HLSL, registry, ShaderVerificationRequirement.PROVIDER_PIPELINE,
                        "D3D12", execute));
    }

    private final class Job implements ShaderPreparationOperation, Runnable {
        private final ShaderPipelineRequest request;
        private final MemorySegment device;
        private final AtomicReference<Packet> output = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final ShaderPreparationTrace trace = new ShaderPreparationTrace();
        private volatile boolean done, cancelled;
        private Throwable failure;
        private boolean finished, disposed;
        private ShaderModuleDescriptor source;
        private boolean refreshedTranslation;
        private final Consumer<Runnable> execute = work -> { checkCancelled(); executor.execute(trace.wrap(work)); };

        Job(ShaderPipelineRequest request, MemorySegment device) { this.request = request; this.device = device; }

        @Override
        public void run() {
            try {
                checkCancelled();
                trace.enter(ShaderPreparationPhase.SOURCE);
                source = request.sourceDescriptor();
                checkCancelled();
                trace.enter(ShaderPreparationPhase.TRANSLATION);
                beginTranslation(compilers);
            } catch (Throwable error) { fail(error); }
        }

        private void beginTranslation(ShaderCompilerRegistry registry) {
            ShaderCompilationTasks.then(translateAsync(source, registry, execute), execute, translated -> {
                beginNative(translated);
                return FdxFuture.completed(null);
            }).onFailure(this::fail);
        }

        private void beginNative(ShaderModuleDescriptor translated) {
            try {
                checkCancelled();
                var state = request.pipelineDescriptor(new MetadataModule(translated.reflection()));
                state.validate(capabilities);
                ShaderRenderBindings resources = ShaderRenderBindings.from(state);
                if (cache == null || !cache.enabled()) {
                    compile(translated, state, resources, null, null, null, null, null);
                    return;
                }
                trace.enter(ShaderPreparationPhase.CACHE_LOOKUP);
                ShaderCacheKey vertexKey = stages.cacheKey(translated.hlslVertexSource(), translated.vertexEntryPoint(), "vs_6_0");
                ShaderCacheKey fragmentKey = stages.cacheKey(translated.hlslFragmentSource(), translated.fragmentEntryPoint(), "ps_6_0");
                CompletableFuture<byte[]> vertex = new CompletableFuture<>(), fragment = new CompletableFuture<>();
                CompletableFuture<D3D12PipelineCache.Entry> pipeline = new CompletableFuture<>();
                ShaderCacheKey pipelineKey = D3D12PipelineCache.key(pipelineCacheIdentity, vertexKey, fragmentKey,
                        state, D3D12Device.VertexInputs.from(state.vertexLayouts()), D3D12Device.PipelineBindings.from(resources));
                cache.readAsync(vertexKey).onSuccess(vertex::complete);
                cache.readAsync(fragmentKey).onSuccess(fragment::complete);
                D3D12PipelineCache.read(cache, pipelineKey).onSuccess(pipeline::complete);
                // I/O latency occupies no compiler worker. Only completed payloads enter native work.
                CompletableFuture.allOf(vertex, fragment, pipeline).thenRun(() -> {
                    try {
                        checkCancelled();
                        execute.accept(() -> compile(translated, state, resources, vertexKey, fragmentKey,
                                vertex.getNow(null), fragment.getNow(null), pipeline.getNow(null)));
                    } catch (Throwable error) { fail(error); }
                });
            } catch (Throwable error) { fail(error); }
        }

        private void compile(ShaderModuleDescriptor translated, RenderPipelineDescriptor state,
                ShaderRenderBindings resources, ShaderCacheKey vertexKey, ShaderCacheKey fragmentKey,
                byte[] vertexBytes, byte[] fragmentBytes, D3D12PipelineCache.Entry cachedPipeline) {
            boolean transferredDevice = false;
            boolean retryTranslation = false;
            try {
                checkCancelled();
                var bindings = D3D12Device.PipelineBindings.from(resources);
                var inputs = D3D12Device.VertexInputs.from(state.vertexLayouts());
                for (int attempt = 0; attempt < 2; attempt++) {
                    trace.enter(ShaderPreparationPhase.COMPILATION);
                    try (var vertex = stages.acquire(translated.hlslVertexSource(), translated.vertexEntryPoint(),
                                 "vs_6_0", translated.label(), vertexKey, attempt == 0 ? vertexBytes : null);
                         var fragment = stages.acquire(translated.hlslFragmentSource(), translated.fragmentEntryPoint(),
                                 "ps_6_0", translated.label(), fragmentKey, attempt == 0 ? fragmentBytes : null)) {
                        checkCancelled();
                        trace.enter(ShaderPreparationPhase.PIPELINE);
                        D3D12FfmContext.Pipeline pipeline;
                        try {
                            pipeline = D3D12FfmContext.createPreparedPipeline(device,
                                    vertex.blob, fragment.blob, state.colorFormat().ordinal(),
                                    state.primitiveTopology().ordinal(), state.depthTestEnabled(), state.depthWriteEnabled(),
                                    state.colorTargets().length > 0 && state.colorTargets()[0].blend() != null,
                                    state.sampledTextureCount(), bindings.uniformGroup, bindings.uniformBinding,
                                    inputs.layoutStrides, inputs.layoutStepModes, inputs.locations, inputs.formats,
                                    inputs.offsets, inputs.slots, bindings.textureGroups, bindings.textureBindings,
                                    bindings.samplerGroups, bindings.samplerBindings, state,
                                    attempt == 0 ? cachedPipeline : cachedPipeline == null ? null
                                            : new D3D12PipelineCache.Entry(cache, cachedPipeline.key(), null));
                        } catch (RuntimeException error) {
                            if (attempt != 0 || !vertex.persisted() && !fragment.persisted()) throw error;
                            stages.reject(vertex, vertexKey);
                            stages.reject(fragment, fragmentKey);
                            continue;
                        }
                        output.set(new Packet(pipeline, device, resources));
                        transferredDevice = true;
                        break;
                    }
                }
            } catch (Throwable error) {
                if (!refreshedTranslation && cache != null && cache.enabled()
                        && error instanceof RuntimeException && !(error instanceof CancellationException)
                        && !cancelled && !closed) {
                    refreshedTranslation = true;
                    retryTranslation = true;
                } else failure = error;
            } finally {
                if (!retryTranslation) completeWork(transferredDevice);
            }
            if (retryTranslation) {
                try {
                    checkCancelled(); trace.enter(ShaderPreparationPhase.TRANSLATION);
                    beginTranslation(refreshCompilers);
                } catch (Throwable error) { fail(error); }
            }
        }

        private void fail(Throwable error) {
            if (completed.get()) return;
            failure = error;
            completeWork(false);
        }

        private void completeWork(boolean transferredDevice) {
            if (!completed.compareAndSet(false, true)) return;
            try {
                if (!transferredDevice) D3D12Ffm.release(device);
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            } finally {
                trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT);
                done = true;
                if (cancelled || closed || failure != null) discard();
                // Keep unpublished successful jobs visible to close() for abandoned-result cleanup.
                if (output.get() == null) jobs.remove(this);
            }
        }

        private void checkCancelled() {
            if (cancelled || closed) throw new CancellationException("D3D12 preparation cancelled");
        }
        private void discard() {
            Packet packet = output.getAndSet(null);
            if (packet != null) packet.close();
        }
        @Override
        public boolean isDone() { context.detectDeviceLoss(); return done; }
        @Override
        public ShaderPreparationPhase phase() { return trace.phase(); }
        @Override
        public ShaderPreparationTrace trace() { return trace; }
        @Override
        public ShaderPreparedResult finish() {
            if (!done || finished) throw new FdxException("D3D12 preparation cannot be published now");
            finished = true;
            context.detectDeviceLoss();
            checkCancelled();
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException error) throw error;
            if (failure != null) throw new FdxException("D3D12 preparation failed", failure);
            Packet packet = output.getAndSet(null);
            if (packet == null) throw new CancellationException("D3D12 prepared result was retired");
            try {
                long handle = D3D12Native.publishPreparedPipeline(context.nativeHandle(), packet.pipeline);
                packet.pipeline = null;
                D3D12Pipeline pipeline;
                try {
                    pipeline = new D3D12Pipeline(context, handle, packet.resources.sampledTextureCount(),
                            packet.resources, request.targetLayout());
                } catch (RuntimeException | Error failure) {
                    D3D12Native.destroyPipeline(context.nativeHandle(), handle);
                    throw failure;
                }
                try {
                    return new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(), pipeline,
                            packet.resources.layout(), request.providerRevision()), pipeline);
                } catch (RuntimeException | Error failure) {
                    pipeline.dispose();
                    throw failure;
                }
            } finally {
                packet.close();
                jobs.remove(this);
            }
        }
        @Override
        public void cancel() {
            cancelled = true;
            if (done) { discard(); jobs.remove(this); }
        }
        @Override
        public void dispose() {
            if (disposed) return;
            if (!done) throw new FdxException("D3D12 native preparation has not drained");
            disposed = true;
            discard();
            jobs.remove(this);
        }
        @Override
        public boolean isDisposed() { return disposed; }
    }

    private static final class Packet implements AutoCloseable {
        D3D12FfmContext.Pipeline pipeline;
        final MemorySegment device;
        final ShaderRenderBindings resources;
        boolean closed;
        Packet(D3D12FfmContext.Pipeline pipeline, MemorySegment device, ShaderRenderBindings resources) {
            this.pipeline = pipeline; this.device = device; this.resources = resources;
        }
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try { if (pipeline != null) pipeline.close(); }
            finally { pipeline = null; D3D12Ffm.release(device); }
        }
    }

    /** Pure worker-owned reflection view; it never enters a context or native resource registry. */
    private record MetadataModule(ShaderReflection reflection) implements ShaderModule {
        @Override
        public ShaderLanguage language() { return ShaderLanguage.HLSL; }
        @Override
        public ProviderId providerId() { return D3D12Provider.ID; }
        @Override
        public <T> T as() { throw new FdxException("Preparation metadata has no native module"); }
        @Override
        public void dispose() { }
        @Override
        public boolean isDisposed() { return false; }
    }
}
