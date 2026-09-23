package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUComputePipeline;
import com.github.xpenatan.webgpu.WGPUComputePipelineDescriptor;
import com.github.xpenatan.webgpu.WGPUCreateComputePipelineAsyncCallback;
import com.github.xpenatan.webgpu.WGPUCreatePipelineAsyncStatus;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOrigin;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadCapture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadDiscovery;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadManifest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadRecipe;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadResolver;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import java.util.concurrent.CancellationException;
import java.util.Map;
import org.teavm.jso.browser.Window;

/** Browser-only native ownership fixtures, kept outside the executable test namespace. */
public final class WGPUWebPreparationChecks implements ShaderProvider {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            @compute @workgroup_size(1) fn computeMain() {}
            """;
    private final GraphicsContext graphics;
    private final WGPUContext context;
    private final ShaderPreparationOperation[] jobs = new ShaderPreparationOperation[4];
    private ShaderPreparedResult ready;
    private int sources, settled, computes;
    private boolean verified;
    private ShaderPreparation replay;
    private ShaderPreparationScope preload;
    private ShaderRequest replayRequest;
    private ShaderPreparationOrigin origin;
    private boolean replayVerified;
    private final long start = System.currentTimeMillis();

    public WGPUWebPreparationChecks(GraphicsContext graphics) {
        this.graphics = graphics;
        context = graphics.as();
        var capabilities = graphics.device().shaderPreparationCapabilities();
        check(capabilities.cpuExecution() == ShaderPreparationCapabilities.Execution.OWNER_THREAD
                && capabilities.nativeExecution() == ShaderPreparationCapabilities.Execution.NATIVE_ASYNC
                && !capabilities.runtimeNonblocking() && capabilities.workerLimit() == 0, "Wrong browser strategy");
        cacheCompletionOnlyQueuesLoadingWork();
        var queued = graphics.device().prepareRenderPipeline(packet(false));
        check(!queued.isDone() && sources == 0, "Polling ran a Java generator");
        queued.cancel();
        check(queued.isDone() && sources == 0, "Queued cancellation ran a Java generator");
        cancelled(queued);
        queued.dispose();
        for (int i = 0; i < 3; i++) {
            jobs[i] = graphics.device().prepareRenderPipeline(packet(i == 1));
            jobs[i].advanceLoading();
            check(!jobs[i].isDone(), "Native completion was synchronous");
        }
        jobs[2].cancel();
        check(!jobs[2].isDone(), "Cancellation freed an active native request");
        WGPUWebPreparation closing = new WGPUWebPreparation();
        closing.initialize(context, 0);
        jobs[3] = closing.submit(packet(false));
        jobs[3].advanceLoading();
        closing.close();
        check(!jobs[3].isDone(), "Service close did not retain the native request");
        compute(false);
        compute(true);
        captureAndPreload();
    }

    @Override
    public GraphicsDevice preparationDevice() { return graphics.device(); }

    private void cacheCompletionOnlyQueuesLoadingWork() {
        FdxFuture<byte[]> read = FdxFuture.pending();
        ShaderArtifactCache cache = new ShaderArtifactCache(new ShaderCacheStore() {
            @Override
            public FdxFuture<byte[]> readAsync(String key) { return read; }
            @Override
            public FdxFuture<Void> writeAsync(String key, byte[] bytes) { return FdxFuture.completed(null); }
            @Override
            public FdxFuture<Void> removeAsync(String key) { return FdxFuture.completed(null); }
        });
        ShaderArtifactCache previous = context.configuration().shaderCache();
        WGPUWebPreparation isolated = new WGPUWebPreparation();
        context.configuration().shaderCache(cache);
        isolated.initialize(context, 0);
        context.configuration().shaderCache(previous);
        var packet = new ShaderPipelineRequest(ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("cache-wait", SOURCE)),
                new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                        .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
        var job = isolated.submit(packet);
        job.advanceLoading();
        check(!job.isDone(), "Cache wait finished early");
        read.complete(null);
        for (int i = 0; i < 12; i++) check(!job.isDone(), "Ordinary polling started cache continuation work");
        check(cache.metrics(ShaderCacheLayer.SOURCE).compilerInvocations() == 0,
                "Storage callback compiled outside explicit loading");
        job.cancel();
        check(job.isDone(), "Cache-wait cancellation retained a device with no native request");
        cancelled(job);
        job.dispose();
        isolated.close();
        System.out.println("WEBGPU_CACHE_LOADING_PASS polls=12 compiler_invocations=0 cancelled=1");
    }
    @Override
    public boolean supports(ShaderRequest request) { return true; }
    @Override
    public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
        return graphics.device().prepareRenderPipeline(packet(false));
    }

    private void captureAndPreload() {
        replayRequest = ShaderRequest.builder(ShaderPassId.FORWARD).variantKey("browser-replay")
                .renderPass(RenderPassCompatibility.layout(RenderTargetLayout.color(graphics.surfaceFormat()))).build();
        origin = new ShaderPreparationOrigin("browser fixture", "triangle", "green", "level",
                new ShaderPreloadRecipe("browser-triangle", 1, "surface", Map.of(), Map.of()));
        var cold = new ShaderPreparation(graphics);
        ShaderPreloadCapture capture = cold.captureRuntime("runtime miss");
        var miss = cold.request(this, replayRequest);
        miss.recordDraws(origin, 1, true);
        cold.update();
        check(miss.state() == ShaderPreparationState.UNSUPPORTED, "Browser runtime miss must remain loading-only");
        var observed = capture.snapshot();
        check(observed.discoveries().getFirst().cause() == ShaderPreloadDiscovery.Cause.UNSUPPORTED
                && observed.discoveries().getFirst().failure().contains("loading-only"),
                "Runtime miss did not explain the loading-only capability");
        String json = observed.manifest().toJson();
        capture.dispose();
        miss.dispose();
        cold.disposeAsync();
        cold.update();
        replay = new ShaderPreparation(graphics);
        preload = replay.createScope("captured level");
        var imported = preload.include(ShaderPreloadManifest.fromJson(json), recipe -> {
            check(recipe.equals(origin.recipe()), "Recipe changed during export/import");
            return ShaderPreloadResolver.Resolution.resolved(this, replayRequest);
        });
        check(imported.resolvedCount() == 1 && !imported.hasUnresolvedEntries(), "Captured recipe did not resolve");
        replay.prepareAsync(preload.seal());
    }

    private void updateReplay() {
        if (replayVerified) return;
        replay.updateLoading();
        if (replay.hasPendingWork()) return;
        check(preload.readyCount() == 1, "Imported preload did not prepare");
        int generatedBefore = sources;
        var capture = replay.captureRuntime("preloaded gameplay");
        var handle = replay.request(this, replayRequest);
        check(handle.readyPass() != null, "Gameplay did not reuse preloaded pipeline");
        handle.recordDraws(origin, 1, false);
        replay.update();
        var observed = capture.snapshot().discoveries().getFirst();
        check(observed.cause() == ShaderPreloadDiscovery.Cause.NONE && observed.skippedDraws() == 0
                && sources == generatedBefore, "Preloaded gameplay compiled or skipped a shader");
        capture.dispose();
        handle.dispose();
        replayVerified = true;
        System.out.println("WEBGPU_REPLAY_PASS runtime_miss_captured=1 recipe_imported=1 ready_reused=true runtime_compiles=0 skipped=0");
    }

    private ShaderPipelineRequest packet(boolean invalidEntry) {
        return new ShaderPipelineRequest(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
            sources++;
            return source();
        }), new RenderPipelineDescriptor().label("browser callback check")
                .renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                .vertexEntryPoint(invalidEntry ? "missingVertex" : "vertexMain")
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }

    private static ShaderModuleDescriptor source() {
        ShaderTarget target = ShaderTarget.WGPU_WGSL;
        // Claim the missing entry only in this fixture so rejection reaches the GPU async callback.
        ShaderReflection reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU).complete(true)
                .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("missingVertex", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build();
        ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(target.id(), target.format(), target.environment(),
                new ShaderStageArtifact[] { ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), SOURCE) },
                ShaderTranslatedInterface.identity(reflection, null), ShaderCompilerId.of("test.webgpu-callbacks"), "1", "");
        artifact = artifact.withVerification(ShaderTargetVerification.providerPipeline(target.environment(), null,
                artifact.compileCacheKey()));
        return ShaderModuleDescriptors.descriptor(artifact, "browser callback fixture", SOURCE);
    }

    private void compute(boolean invalid) {
        WGPUShaderModuleHandle module = WGPUGraphicsDevice.createNativeShader(context.nativeDevice(),
                context.resourceDomain(), null, source());
        WGPUComputePipelineDescriptor descriptor = new WGPUComputePipelineDescriptor();
        descriptor.setLabel("browser compute callback");
        descriptor.getCompute().setModule(module.nativeModule());
        descriptor.getCompute().setEntryPoint(invalid ? "missingCompute" : "computeMain");
        WGPUCreateComputePipelineAsyncCallback callback = new WGPUCreateComputePipelineAsyncCallback() {
            @Override
            protected void onCallback(WGPUCreatePipelineAsyncStatus status, WGPUComputePipeline pipeline, String message) {
                Window.setTimeout(() -> {
                    try {
                        check(status == (invalid ? WGPUCreatePipelineAsyncStatus.ValidationError
                                : WGPUCreatePipelineAsyncStatus.Success), "Compute callback status: " + status + " " + message);
                        computes++;
                    } finally {
                        if (pipeline.isValid()) pipeline.release();
                        pipeline.dispose();
                        descriptor.dispose();
                        module.dispose();
                        dispose();
                    }
                }, 0);
            }
        };
        context.nativeDevice().createComputePipelineAsync(descriptor, WGPUCallbackMode.AllowSpontaneous, callback);
    }

    public void render() {
        check(verified || System.currentTimeMillis() - start < 10_000, "Browser callbacks did not drain");
        updateReplay();
        for (int i = 0; i < jobs.length; i++) {
            var job = jobs[i];
            if (job == null || !job.isDone()) continue;
            if (i == 0) ready = job.finish();
            else if (i == 1) {
                try { job.finish(); throw new AssertionError("Invalid native pipeline succeeded"); }
                catch (FdxException expected) { check(expected.getMessage().contains("ValidationError"), expected.getMessage()); }
            } else cancelled(job);
            job.dispose();
            jobs[i] = null;
            settled++;
        }
        var frame = graphics.currentFrame();
        var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
        if (ready != null) { pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); }
        pass.end();
        if (!verified && settled == jobs.length && computes == 2 && replayVerified) {
            verified = true;
            System.out.println("WEBGPU_CALLBACKS_PASS queued_cancel=1 active_cancel=1 service_close=1 render_success=1 render_error=1 compute_success=1 compute_error=1");
        }
    }

    public void dispose() {
        check(verified, "Browser checks exited before completion");
        ready.dispose();
        preload.dispose();
        replay.disposeAsync();
        replay.update();
        // Backend destruction follows this application callback; the outstanding native request must retain its device.
        var late = graphics.device().prepareRenderPipeline(packet(false));
        late.advanceLoading();
        check(!late.isDone(), "Teardown fixture must have an active callback");
        long deadline = System.currentTimeMillis() + 5_000;
        Window.setTimeout(() -> verifyTeardown(late, deadline), 0);
    }

    private void verifyTeardown(ShaderPreparationOperation late, long deadline) {
        check(System.currentTimeMillis() < deadline, "Device teardown callback did not drain");
        if (!late.isDone()) {
            check(!context.nativeDevice().isDisposed(), "Device released before callback completion");
            Window.setTimeout(() -> verifyTeardown(late, deadline), 1);
            return;
        }
        cancelled(late);
        late.dispose();
        check(context.nativeDevice().isDisposed(), "Retained device leaked after teardown");
        System.out.println("WEBGPU_TEARDOWN_PASS late_callback_discarded=true device_released=true");
    }

    private static void cancelled(ShaderPreparationOperation operation) {
        try { operation.finish(); throw new AssertionError("Cancelled pipeline was published"); }
        catch (CancellationException expected) { }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
