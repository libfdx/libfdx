package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.target.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GLPreparationTest {
    @Test
    void loadingBudgetYieldsBetweenContinuationsAndLaterAdvancesComplete() {
        Fixture f = new Fixture(); f.loading = true; f.gl.loadingBudget = 1;
        f.operation.prepareLoading(); f.operation.advanceLoading();
        assertEquals(0,f.gl.compiles);
        f.gl.linkComplete = true;
        for (int i=0;i<100&&!f.operation.isDone();i++) f.operation.advanceLoading();
        assertTrue(f.operation.isDone()); assertEquals(2,f.gl.compiles);
        f.operation.finish().dispose(); f.operation.dispose(); f.attachment.dispose();
    }
    @Test
    void pendingAsyncSourceNeverCompilesOnPollAndFailureOrCancellationCannotSubmitGpuWork() {
        for (int outcome = 0; outcome < 3; outcome++) {
            Fixture f = new Fixture(); f.loading = true;
            var pending = FdxFuture.<ShaderModuleDescriptor>pending();
            var source = io.github.libfdx.graphics.shader.ShaderModuleSource.deferred("vertexMain","fragmentMain",
                    () -> { throw new AssertionError("Sync source invoked"); }, execute -> pending);
            var request = new ShaderPipelineRequest(source, new RenderPipelineDescriptor().colorFormat(TextureFormat.RGBA8_UNORM),
                    ShaderPassId.FORWARD,0);
            var operation = new GLPreparationOperation((GLGraphicsDevice) f.attachment.device(), f.attachment,
                    f.gl.api,f.provider,f.attachment.resourceDomain(),request,f.compilers,f.compilers,null);
            operation.prepareLoading(); operation.advanceLoading();
            assertFalse(operation.isDone()); assertEquals(0,f.gl.compiles);
            if (outcome == 2) operation.cancel();
            if (outcome == 1) pending.completeExceptionally(new IllegalStateException("source failed"));
            else pending.complete(f.request.sourceDescriptor());
            assertEquals(0,f.gl.compiles);
            operation.advanceLoading();
            if (outcome == 0) {
                assertEquals(2,f.gl.compiles); f.gl.linkComplete = true;
                assertTrue(operation.isDone()); operation.finish().dispose();
            } else {
                assertTrue(operation.isDone()); assertEquals(0,f.gl.compiles);
                assertThrows(RuntimeException.class,operation::finish);
            }
            operation.dispose(); f.attachment.dispose();
        }
    }
    @Test
    void delayedTranslationCompletionOnlyQueuesWorkerWorkAndOwnerPublishes() throws Exception {
        Fixture f = new Fixture(); f.heldTranslation = FdxFuture.pending();
        f.operation.prepareAsync(); f.gl.runWorkers();
        assertNotNull(f.heldRequest);
        assertFalse(f.operation.isDone()); assertEquals(0, f.gl.compiles);
        f.heldTranslation.complete(f.translationResult(f.heldRequest));
        assertFalse(f.operation.isDone()); assertEquals(0, f.gl.compiles);
        f.gl.runWorkers();
        assertFalse(f.operation.isDone()); assertEquals(2, f.gl.compiles);
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        var result = f.operation.finish(); f.operation.dispose(); result.dispose(); f.attachment.dispose();
    }

    @Test
    void delayedTranslationAfterTeardownDrainsWhenWorkerContinuationIsRejected() throws Exception {
        Fixture f = new Fixture(); f.heldTranslation = FdxFuture.pending();
        f.operation.prepareAsync(); f.gl.runWorkers();
        f.operation.close(false); f.gl.closed = true;
        assertFalse(f.operation.isDone());
        f.heldTranslation.complete(f.translationResult(f.heldRequest));
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.compiles);
    }

    @Test
    void rejectedRestoredTranslationRetriesOnlyOnceAndDisposesBothPrograms() throws Exception {
        Fixture f = new Fixture();
        var refresh = ShaderCompilerRegistry.builder().compiler(f.compiler).build();
        var operation = new GLPreparationOperation((GLGraphicsDevice) f.attachment.device(),
                f.attachment, f.gl.api, f.provider, f.attachment.resourceDomain(), f.request, f.compilers, refresh, null);
        operation.prepareAsync(); f.gl.runWorkers(); operation.isDone();
        f.gl.linkComplete = true; f.gl.linkValid = false;
        assertFalse(operation.isDone());
        assertEquals(1, f.gl.deletedPrograms);
        assertFalse(operation.isDone()); assertEquals(2, f.gl.compiles);
        f.gl.runWorkers(); assertFalse(operation.isDone());
        assertTrue(operation.isDone());
        assertThrows(FdxException.class, operation::finish);
        operation.dispose(); f.attachment.dispose();
        assertEquals(4, f.gl.compiles); assertEquals(2, f.gl.deletedPrograms);
    }

    @Test
    void sourceWorkNeverCallsGLAndLinkStatusWaitsForProgramCompletion() throws Exception {
        Fixture f = new Fixture();
        assertFalse(f.operation.isDone());
        f.generate();
        assertEquals(0, f.gl.compiles);
        assertFalse(f.operation.isDone());
        assertEquals(2, f.gl.compiles); assertEquals(0, f.gl.shaderStatuses);
        for (int i = 0; i < 5; i++) assertFalse(f.operation.isDone());
        assertEquals(1, f.gl.links); assertEquals(0, f.gl.shaderStatuses);
        assertFalse(f.operation.isDone()); assertEquals(0, f.gl.linkStatuses);
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        var result = f.operation.finish();
        assertNotNull(result.pass().pipeline()); assertEquals(1, f.gl.linkStatuses);
        f.operation.dispose(); result.dispose(); f.attachment.dispose();
        assertEquals(1, f.gl.deletedPrograms); assertEquals(2, f.gl.deletedShaders);
    }

    @Test
    void failedLinkDisposesAllPartialObjectsAfterCompletion() throws Exception {
        Fixture f = new Fixture(); f.generate();
        assertFalse(f.operation.isDone());
        f.gl.linkComplete = true; f.gl.linkValid = false;
        assertTrue(f.operation.isDone());
        assertThrows(FdxException.class, f.operation::finish);
        assertEquals(1, f.gl.links); assertEquals(2, f.gl.deletedShaders);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(2, f.gl.deletedShaders);
    }

    @Test
    void cancellationBeforeSourceExecutionDoesNotSubmitNativeWork() throws Exception {
        Fixture f = new Fixture(); f.operation.cancel(); f.generate();
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        assertEquals(0, f.gl.compiles);
        f.operation.dispose(); f.attachment.dispose();
    }

    @Test
    void contextLossAbandonsNativeNamesWithoutUsingTheLostContext() throws Exception {
        Fixture f = new Fixture(); f.generate(); f.operation.isDone();
        f.gl.lost = true; f.operation.close(true);
        assertTrue(f.operation.isDone());
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedShaders); assertEquals(0, f.gl.deletedPrograms);
    }

    @Test
    void completedPipelineCannotPublishAfterLossWithoutAFramePoll() throws Exception {
        Fixture f = new Fixture(); f.generate(); f.operation.isDone();
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        f.gl.lost = true;
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedPrograms);
    }

    @Test
    void nativePollingDetectsLossWithoutAnEarlierDomainQuery() throws Exception {
        Fixture f = new Fixture(); f.generate(); f.operation.isDone();
        f.gl.lost = true;
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedPrograms); assertEquals(0, f.gl.deletedShaders);
    }

    @Test
    void surfaceLossRejectsAlreadyCompletedPipelineWithoutAResetStatusSignal() throws Exception {
        Fixture f = new Fixture(); f.generate(); f.operation.isDone();
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        f.attachment.beginFrame(); f.surfaceLostAtSwap = true;
        assertThrows(GraphicsContextLostException.class, f.attachment::endFrame);
        assertFalse(f.gl.lost, "The surface alone must invalidate preparation");
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedPrograms);
    }

    @Test
    void loadingAfterLossDoesNotStartSourceOrNativeWork() {
        Fixture f = new Fixture(); f.loading = true; f.heldTranslation = FdxFuture.pending();
        f.operation.prepareLoading();
        f.gl.lost = true;
        f.operation.advanceLoading();
        assertNull(f.heldRequest, "Lost context must not start queued source/translation work");
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.compiles);
    }

    @Test
    void cancellationDetectsLossBeforeDeletingNativeNames() throws Exception {
        Fixture f = new Fixture(); f.generate(); f.operation.isDone();
        f.gl.lost = true;
        f.operation.cancel();
        assertTrue(f.operation.isDone());
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedPrograms); assertEquals(0, f.gl.deletedShaders);
    }

    @Test
    void resourceDomainQueryDetectsLossAndNeverRevives() {
        Fixture f = new Fixture();
        Object original = f.attachment.device().resourceDomain();
        f.gl.lost = true;
        Object invalidated = f.attachment.device().resourceDomain();
        assertNotSame(original, invalidated);
        f.gl.lost = false; // A restored native context does not restore the old resources.
        assertSame(invalidated, f.attachment.device().resourceDomain());
        f.attachment.dispose();
    }

    @Test
    void workerCompletionNeedsExplicitLoadingAdvanceWithoutDriverPolling() throws Exception {
        Fixture f = new Fixture();
        f.gl.parallel = false; f.gl.workers = 2;
        var capabilities = f.attachment.device().shaderPreparationCapabilities();
        assertEquals(ShaderPreparationCapabilities.Execution.WORKERS, capabilities.cpuExecution());
        assertEquals(ShaderPreparationCapabilities.Execution.OWNER_THREAD, capabilities.nativeExecution());
        assertEquals(2, capabilities.workerLimit());
        assertFalse(capabilities.runtimeNonblocking());
        f.operation.advanceLoading();
        assertFalse(f.operation.isDone());
        assertEquals(0, f.gl.compiles);
        f.generate();
        for (int i = 0; i < 5; i++) assertFalse(f.operation.isDone());
        assertEquals(0, f.gl.compiles);
        f.gl.linkComplete = true;
        f.operation.advanceLoading();
        assertEquals(2, f.gl.compiles); assertEquals(1, f.gl.links);
        assertEquals(0, f.gl.completionQueries);
        assertTrue(f.operation.isDone());
        assertEquals(0, f.gl.completionQueries);
        var result = f.operation.finish();
        f.operation.dispose(); result.dispose(); f.attachment.dispose();
    }

    @Test
    void cancelledLoadingOnlyWorkDrainsWithoutAnyNativeCompile() throws Exception {
        Fixture f = new Fixture(); f.gl.parallel = false;
        f.operation.cancel(); f.generate();
        f.operation.advanceLoading();
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        assertEquals(0, f.gl.compiles);
        f.operation.dispose(); f.attachment.dispose();
    }

    @Test
    void ownerSourceGenerationCanUseDriverPollingDuringExplicitLoading() {
        Fixture f = new Fixture(); f.loading = true;
        var capabilities = f.attachment.device().shaderPreparationCapabilities();
        assertEquals(ShaderPreparationCapabilities.Execution.OWNER_THREAD, capabilities.cpuExecution());
        assertEquals(ShaderPreparationCapabilities.Execution.DRIVER_POLLING, capabilities.nativeExecution());
        assertFalse(capabilities.runtimeNonblocking());
        f.operation.prepareLoading();
        assertEquals(0, f.gl.compiles);
        assertFalse(f.operation.isDone());
        assertEquals(0, f.gl.links);
        f.operation.advanceLoading();
        assertEquals(1, f.gl.links);
        assertFalse(f.operation.isDone()); assertEquals(0, f.gl.linkStatuses);
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        var result = f.operation.finish(); f.operation.dispose(); result.dispose(); f.attachment.dispose();
    }

    @Test
    void ownerPreparationWithoutDriverPollingCompletesOnlyDuringLoading() {
        Fixture f = new Fixture(); f.loading = true; f.gl.parallel = false;
        f.operation.prepareLoading();
        for (int i = 0; i < 12; i++) assertFalse(f.operation.isDone());
        assertEquals(0, f.gl.compiles);
        f.gl.linkComplete = true;
        f.operation.advanceLoading();
        assertTrue(f.operation.isDone());
        assertEquals(2, f.gl.compiles); assertEquals(1, f.gl.links);
        assertEquals(0, f.gl.completionQueries);
        var result = f.operation.finish(); f.operation.dispose(); result.dispose(); f.attachment.dispose();
    }

    @Test
    void ownerTranslationCompletionWaitsForAnotherExplicitLoadingAdvance() {
        Fixture f = new Fixture(); f.loading = true; f.heldTranslation = FdxFuture.pending();
        f.operation.prepareLoading();
        for (int i = 0; i < 12; i++) assertFalse(f.operation.isDone());
        assertNull(f.heldRequest);
        f.operation.advanceLoading();
        assertNotNull(f.heldRequest);
        f.heldTranslation.complete(f.translationResult(f.heldRequest));
        for (int i = 0; i < 12; i++) assertFalse(f.operation.isDone());
        assertEquals(0, f.gl.compiles); assertEquals(0, f.gl.links);
        f.operation.advanceLoading();
        assertEquals(2, f.gl.compiles); assertEquals(1, f.gl.links);
        f.gl.linkComplete = true;
        assertTrue(f.operation.isDone());
        var result = f.operation.finish(); f.operation.dispose(); result.dispose(); f.attachment.dispose();
    }

    @Test
    void ownerTranslationAfterCancellationOrContextLossNeverUsesDisposedContext() {
        for (boolean contextLost : new boolean[] {false, true}) {
            Fixture f = new Fixture(); f.loading = true; f.heldTranslation = FdxFuture.pending();
            f.operation.prepareLoading(); f.operation.advanceLoading();
            assertNotNull(f.heldRequest);
            if (contextLost) { f.gl.lost = true; f.attachment.detectContextLoss(); }
            else f.operation.cancel();
            assertTrue(f.operation.isDone());
            assertThrows(CancellationException.class, f.operation::finish);
            f.operation.dispose(); f.attachment.dispose();
            f.heldTranslation.complete(f.translationResult(f.heldRequest));
            f.operation.advanceLoading();
            assertTrue(f.operation.isDone());
            assertEquals(0, f.gl.compiles); assertEquals(0, f.gl.links);
        }
    }

    @Test
    void ownerRejectedTranslationRebuildsOnlyDuringLoadingAndOnlyOnce() {
        Fixture f = new Fixture(); f.loading = true;
        var refresh = ShaderCompilerRegistry.builder().compiler(f.compiler).build();
        var operation = new GLPreparationOperation((GLGraphicsDevice) f.attachment.device(),
                f.attachment, f.gl.api, f.provider, f.attachment.resourceDomain(), f.request, f.compilers, refresh, null);
        operation.prepareLoading(); operation.advanceLoading();
        f.gl.linkComplete = true; f.gl.linkValid = false;
        assertFalse(operation.isDone());
        for (int i = 0; i < 12; i++) assertFalse(operation.isDone());
        assertEquals(2, f.gl.compiles); assertEquals(1, f.gl.deletedPrograms);
        operation.advanceLoading();
        assertTrue(operation.isDone());
        assertThrows(FdxException.class, operation::finish);
        operation.dispose(); f.attachment.dispose();
        assertEquals(4, f.gl.compiles); assertEquals(2, f.gl.deletedPrograms);
    }

    @Test
    void deviceDomainChangesOnLossAndPendingWorkNeverUsesLostContext() throws Exception {
        Fixture f = new Fixture();
        Object original = f.attachment.device().resourceDomain();
        f.generate(); f.operation.isDone();
        f.gl.lost = true;
        assertTrue(f.attachment.detectContextLoss());
        assertNotSame(original, f.attachment.device().resourceDomain());
        assertTrue(f.operation.isDone());
        assertThrows(CancellationException.class, f.operation::finish);
        f.operation.dispose(); f.attachment.dispose();
        assertEquals(0, f.gl.deletedShaders);
    }

    static final class Fixture {
        boolean surfaceLostAtSwap;
        boolean loading;
        FdxFuture<ShaderTargetCompileResult> heldTranslation;
        ShaderTargetCompileRequest heldRequest;
        final FakeGL gl = new FakeGL();
        final ProviderId provider = ProviderId.of("gl");
        final GLGraphicsAttachment attachment = new GLGraphicsAttachment(provider, gl.api, new GLSurface() {
            @Override
            public void makeCurrent() { }
            @Override
            public void swapBuffers() {
                if (surfaceLostAtSwap) throw new GraphicsContextLostException(provider);
            }
            @Override
            public void releaseCurrent() { }
        }, 64, 64, TextureFormat.RGBA8_UNORM);
        final ShaderReflection reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build();
        final ShaderPipelineRequest request = new ShaderPipelineRequest(
                ShaderModuleDescriptor.wgsl("test", "test source").reflection(reflection),
                new RenderPipelineDescriptor().colorFormat(TextureFormat.RGBA8_UNORM), ShaderPassId.FORWARD, 0);
        final ShaderTargetCompiler compiler = new ShaderTargetCompiler() {
                    @Override
                    public ShaderCompilerId id() { return ShaderCompilerId.of("test"); }
                    @Override
                    public String version() { return "1"; }
                    @Override
                    public ShaderTargetId[] targets() { return new ShaderTargetId[] {ShaderTarget.OPENGL_GLSL.id()}; }
                    @Override
                    public boolean supports(ShaderTargetCompileRequest request) { return true; }
                    @Override
                    public FdxFuture<ShaderTargetCompileResult> compileAsync(ShaderTargetCompileRequest request,
                            Consumer<Runnable> execute) {
                        if (loading) assertSame(gl.owner, Thread.currentThread());
                        else assertNotSame(gl.owner, Thread.currentThread());
                        if (heldTranslation == null) return ShaderTargetCompiler.super.compileAsync(request, execute);
                        heldRequest = request;
                        return heldTranslation;
                    }
                    @Override
                    public ShaderTargetCompileResult compile(ShaderTargetCompileRequest request) {
                        if (loading) assertSame(gl.owner, Thread.currentThread());
                        else assertNotSame(gl.owner, Thread.currentThread());
                        return translationResult(request);
                    }
                };
        final ShaderCompilerRegistry compilers = ShaderCompilerRegistry.builder().compiler(compiler).build();
        final GLPreparationOperation operation = new GLPreparationOperation((GLGraphicsDevice) attachment.device(),
                attachment, gl.api, provider, attachment.resourceDomain(), request, compilers, compilers, null);
        ShaderTargetCompileResult translationResult(ShaderTargetCompileRequest request) {
            ShaderStageArtifact[] stages = new ShaderStageArtifact[request.entryPoints().length];
            for (int i = 0; i < stages.length; i++) {
                var entry = request.entryPoints()[i];
                stages[i] = ShaderStageArtifact.text(entry.stage(), entry.entryPoint(), request.format(), "void main() {}");
            }
            return ShaderTargetCompileResult.success(ShaderTargetArtifact.compiled(request.target(), request.format(),
                    request.environment(), stages, ShaderTranslatedInterface.identity(request.shaderInterface(), request.entryPoints()),
                    compiler.id(), compiler.version(), ShaderTargetCacheKeys.compilation(request, compiler.id(), compiler.version())));
        }
        void generate() throws Exception {
            operation.prepareAsync(); gl.runWorkers();
        }
    }

    static final class FakeGL implements InvocationHandler {
        long loadingBudget;
        final ConcurrentLinkedQueue<Runnable> work = new ConcurrentLinkedQueue<>();
        volatile boolean closed;
        final Thread owner = Thread.currentThread();
        final GLApi api = (GLApi) Proxy.newProxyInstance(GLApi.class.getClassLoader(), new Class<?>[] {GLApi.class}, this);
        int next = 1, compiles, shaderStatuses, links, linkStatuses, deletedShaders, deletedPrograms, completionQueries;
        int workers;
        boolean linkComplete, lost, linkValid = true, parallel = true;
        int binaryRestores, binaryExports, binaryHints;
        boolean binaryAccepted = true, exportFails;
        void runWorkers() throws Exception {
            Thread worker = new Thread(() -> {
                for (Runnable next; (next = work.poll()) != null;) next.run();
            });
            worker.start(); worker.join(5000); assertFalse(worker.isAlive());
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("executeShaderPreparation")) {
                if (closed) throw new FdxException("Test worker executor closed");
                work.add((Runnable) args[0]); return null;
            }
            assertSame(owner, Thread.currentThread(), "Native GL calls must stay on the context owner");
            return switch (method.getName()) {
                case "shaderPreparationWorkers" -> workers;
                case "shaderLoadingBudgetNanos" -> loadingBudget;
                case "supportsParallelShaderCompilation" -> parallel;
                case "isContextLost" -> lost;
                case "compileShader" -> { compiles++; yield null; }
                case "programCompilationComplete" -> { completionQueries++; yield linkComplete; }
                case "shaderCompileStatus" -> { fail("Successful async linking must not query individual shader status"); yield false; }
                case "programLinkStatus" -> { assertTrue(linkComplete); linkStatuses++; yield linkValid; }
                case "shaderInfoLog" -> { assertTrue(linkComplete); yield "invalid test shader"; }
                case "programInfoLog" -> { assertTrue(linkComplete); yield "invalid test link"; }
                case "linkProgram" -> { assertTrue(compiles >= 2); links++; yield null; }
                case "hintProgramBinaryRetrievable" -> { binaryHints++; yield null; }
                case "restoreProgramBinary" -> { binaryRestores++; yield binaryAccepted; }
                case "exportProgramBinary" -> {
                    binaryExports++;
                    if (exportFails) throw new FdxException("Test export unavailable");
                    yield new GLProgramBinary(17, new byte[] {1, 2, 3});
                }
                case "deleteShader" -> { assertFalse(lost); deletedShaders++; yield null; }
                case "deleteProgram" -> { assertFalse(lost); deletedPrograms++; yield null; }
                default -> method.getReturnType() == int.class ? next++ : method.getReturnType() == boolean.class ? false : null;
            };
        }
    }
}
