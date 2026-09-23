package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationTrace;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Source work runs on adapter workers or explicit loading advances. All native state
 * stays on the context owner. Runtime queries poll completion; loading advances may block. */
final class GLPreparationOperation implements ShaderPreparationOperation {
    private final GLGraphicsDevice device;
    private final GLGraphicsAttachment attachment;
    private final GLApi gl;
    private final ProviderId provider;
    private final GLResourceDomain domain;
    private final ShaderPipelineRequest request;
    private final ShaderCompilerRegistry compilers;
    private final ShaderCompilerRegistry refreshCompilers;
    private final GLProgramCache programCache;
    private GLProgramCache.Entry cachedProgram;
    private boolean binaryAttempted, binarySubmitted, retrievable;
    private boolean refreshed;
    private final ShaderPreparationTrace trace = new ShaderPreparationTrace();
    private volatile boolean sourceDone, cancelled;
    private ShaderModuleDescriptor source;
    private Throwable failure;
    private int vertex, fragment, program;
    private ShaderPreparedResult result;
    private boolean done, finished, disposed, lost;
    private boolean sourceOnOwner;
    private final ArrayDeque<Runnable> loadingWork = new ArrayDeque<>();

    GLPreparationOperation(GLGraphicsDevice device, GLGraphicsAttachment attachment, GLApi gl,
            ProviderId provider, GLResourceDomain domain, ShaderPipelineRequest request, ShaderCompilerRegistry compilers,
            ShaderCompilerRegistry refreshCompilers, GLProgramCache programCache) {
        this.device = device; this.attachment = attachment; this.gl = gl; this.provider = provider;
        this.domain = domain; this.request = request; this.compilers = compilers;
        this.refreshCompilers = refreshCompilers;
        this.programCache = programCache;
    }

    /** Starts worker-only source work. Cache waits release the borrowed compiler executor;
     * completion callbacks only publish data for subsequent owner-context advancement. */
    void prepareAsync() {
        prepareSource(gl::executeShaderPreparation);
    }

    /** Storage callbacks enqueue only. CPU work and native submission require advanceLoading(). */
    void prepareLoading() {
        sourceOnOwner = true;
        prepareSource(work -> {
            synchronized (loadingWork) {
                requireInterest();
                loadingWork.add(work);
            }
        });
    }

    private void prepareSource(Consumer<Runnable> executor) {
        Consumer<Runnable> execute = trace.executor(executor);
        ShaderCompilerRegistry registry = refreshed ? refreshCompilers : compilers;
        FdxFuture<ShaderModuleDescriptor> generatedSource = ShaderCompilationTasks.then(ShaderCompilationTasks.submit(execute, () -> {
            requireInterest();
            trace.enter(ShaderPreparationPhase.SOURCE);
            return request.sourceDescriptorAsync(execute);
        }), execute, pending -> pending);
        FdxFuture<ShaderModuleDescriptor> translatedSource = ShaderCompilationTasks.then(generatedSource, execute, generated -> {
            requireInterest();
            trace.enter(ShaderPreparationPhase.TRANSLATION);
            FdxFuture<ShaderModuleDescriptor> reflected = generated.reflection().complete()
                    ? FdxFuture.completed(generated)
                    : ShaderModuleDescriptors.requireTargetAsync(generated, ShaderTarget.WGPU_WGSL, registry,
                            ShaderVerificationRequirement.REQUIRED, "GL reflection", execute);
            return ShaderCompilationTasks.then(reflected, execute, descriptor -> {
                requireInterest();
                return ShaderModuleDescriptors.requireTargetAsync(
                        descriptor.entryPoints(generated.vertexEntryPoint(), generated.fragmentEntryPoint()),
                        ShaderTarget.forProvider(provider), registry, ShaderVerificationRequirement.PROVIDER_PIPELINE, "GL", execute);
            });
        });
        ShaderCompilationTasks.then(translatedSource, execute, translated -> {
            requireInterest();
            source = translated;
            if (programCache != null) trace.enter(ShaderPreparationPhase.CACHE_LOOKUP);
            return programCache == null ? FdxFuture.<GLProgramCache.Entry>completed(null) : programCache.read(translated);
        }).onFailure(error -> { failure = error; sourceDone = true; })
                .onSuccess(entry -> { cachedProgram = entry; sourceDone = true; });
    }

    private void requireInterest() {
        if (cancelled) throw new CancellationException("GL source preparation cancelled");
    }

    @Override
    public void advanceLoading() {
        if (attachment.detectContextLoss()) close(true);
        if (done) return;
        long budget = gl.shaderLoadingBudgetNanos();
        long started = budget > 0 ? System.nanoTime() : 0;
        for (int step = 0; step < 32 && !cancelled; step++) {
            Runnable work;
            synchronized (loadingWork) { work = loadingWork.pollFirst(); }
            if (work == null) break;
            work.run();
            if (budget > 0 && System.nanoTime() - started >= budget) break;
        }
        // At most submit then finish. Pending source/cache work never causes an owner wait.
        if (!advance(true) && sourceDone && !driverPolling()) advance(true);
    }

    @Override
    public boolean isDone() { return advance(false); }

    private boolean driverPolling() {
        return device.shaderPreparationCapabilities().nativeExecution() == ShaderPreparationCapabilities.Execution.DRIVER_POLLING;
    }

    private boolean advance(boolean loading) {
        if (attachment.detectContextLoss()) close(true);
        if (done) return true;
        if (!sourceDone) return false;
        if (cancelled || failure != null) { done = true; return true; }
        boolean polling = driverPolling();
        if (!polling && !loading) return false;
        if (sourceOnOwner && program == 0 && !loading) return false;
        // Loading binary operations (including export after linking) must finish in loading.
        // Binary import/link status has no nonblocking guarantee, even with KHR polling.
        if ((binaryAttempted || retrievable) && !loading) return false;
        try {
            attachment.makeCurrent();
            if (program == 0) {
                if (source.targetArtifact() != null) ShaderTargetSupport.forProvider(provider).require(source.targetArtifact());
                if (loading && !binaryAttempted && cachedProgram != null && cachedProgram.binary() != null) {
                    binaryAttempted = true;
                    binarySubmitted = true;
                    trace.enter(ShaderPreparationPhase.PIPELINE);
                    program = gl.createProgram();
                    programCache.restoring(cachedProgram);
                    boolean accepted;
                    try { accepted = gl.restoreProgramBinary(program, cachedProgram.binary()); }
                    catch (RuntimeException rejected) { accepted = false; }
                    if (!accepted) rejectBinary();
                    return false;
                }
                trace.enter(ShaderPreparationPhase.COMPILATION);
                if (cachedProgram != null) programCache.compiling(cachedProgram);
                vertex = submit(GLShaderType.VERTEX, source.glslVertexSource());
                fragment = submit(GLShaderType.FRAGMENT, source.glslFragmentSource());
                // Link queues the dependency on both compilations. Checking their individual
                // status here can serialize driver work, and is unnecessary on successful links.
                trace.enter(ShaderPreparationPhase.PIPELINE);
                program = gl.createProgram();
                retrievable = loading && programCache != null;
                if (retrievable) {
                    try { gl.hintProgramBinaryRetrievable(program); }
                    catch (RuntimeException unavailable) { retrievable = false; }
                }
                gl.attachShader(program, vertex); gl.attachShader(program, fragment); gl.linkProgram(program);
                return false;
            }
            if (!binarySubmitted && polling && !gl.programCompilationComplete(program)) return false;
            if (!gl.programLinkStatus(program)) {
                if (binarySubmitted) { rejectBinary(); return false; }
                throw new FdxException("GL pipeline preparation failed: " + gl.programInfoLog(program));
            }
            if (loading && retrievable) programCache.save(program, cachedProgram);
            GLShaderModuleHandle module = new GLShaderModuleHandle(provider, gl, domain, program, source.reflection(),
                    source.targetArtifact() != null ? source.targetArtifact().translatedInterface() : null,
                    source.vertexEntryPoint(), source.fragmentEntryPoint());
            program = 0;
            try {
                var descriptor = request.pipelineDescriptor(module);
                ShaderRenderBindings bindings = ShaderRenderBindings.from(descriptor);
                RenderPipeline pipeline = device.createRenderPipeline(descriptor);
                try {
                    result = new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(), pipeline,
                            bindings.layout(), request.providerRevision()), new Disposable() {
                        private boolean disposed;
                        @Override
                        public void dispose() {
                            if (disposed) return;
                            disposed = true;
                            try { pipeline.dispose(); } finally { module.dispose(); }
                        }
                        @Override
                        public boolean isDisposed() { return disposed; }
                    });
                } catch (Throwable error) { pipeline.dispose(); throw error; }
            } catch (Throwable error) { module.dispose(); throw error; }
            clearNative(); trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT); done = true;
        } catch (Throwable error) {
            try { clearNative(); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            if (error instanceof RuntimeException && !refreshed && refreshCompilers != compilers && !cancelled && !lost) {
                // One retry bypasses restored compiler data. Never compile from this owner callback.
                refreshed = true;
                sourceDone = false;
                source = null;
                if (sourceOnOwner) prepareLoading();
                else prepareAsync();
                return false;
            }
            failure = error;
            done = true;
        }
        return done;
    }

    private int submit(GLShaderType type, String text) {
        int shader = gl.createShader(type);
        try {
            gl.shaderSource(shader, GLGraphicsDevice.normalizeGlslSource(text));
            gl.compileShader(shader); return shader;
        } catch (Throwable error) { gl.deleteShader(shader); throw error; }
    }
    private void rejectBinary() {
        programCache.rejected(cachedProgram);
        clearNative();
        binarySubmitted = false;
        // Next advance compiles the existing GLSL once; no repeated binary or Tint retry.
    }
    private void clearNative() {
        int oldProgram = program, oldVertex = vertex, oldFragment = fragment;
        program = vertex = fragment = 0;
        binarySubmitted = retrievable = false;
        if (lost) return;
        try { if (oldProgram != 0) gl.deleteProgram(oldProgram); }
        finally { try { if (oldVertex != 0) gl.deleteShader(oldVertex); }
            finally { if (oldFragment != 0) gl.deleteShader(oldFragment); } }
    }
    @Override
    public ShaderPreparationPhase phase() { return trace.phase(); }
    @Override
    public ShaderPreparationTrace trace() { return trace; }
    @Override
    public ShaderPreparedResult finish() {
        if (!done || finished) throw new FdxException("GL preparation cannot be published now");
        if (attachment.detectContextLoss()) close(true);
        finished = true;
        if (cancelled) throw new CancellationException("GL preparation cancelled");
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new FdxException("GL preparation failed", failure);
        ShaderPreparedResult transfer = result; result = null;
        device.preparationFinished(this); return transfer;
    }
    @Override
    public void cancel() {
        cancelled = true;
        if (sourceOnOwner) {
            synchronized (loadingWork) { loadingWork.clear(); }
            sourceDone = true;
        }
        lost |= attachment.detectContextLoss();
        if (vertex != 0 || fragment != 0 || program != 0) { if (!lost) attachment.makeCurrent(); clearNative(); }
        if (result != null) { result.dispose(); result = null; }
    }
    void close(boolean contextLost) { lost |= contextLost; cancel(); }
    @Override
    public void dispose() {
        if (disposed) return;
        if (!sourceDone) throw new FdxException("GL source preparation has not drained");
        disposed = true; cancel(); device.preparationFinished(this);
    }
    @Override
    public boolean isDisposed() { return disposed; }
}
