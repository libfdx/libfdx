package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.graphics.GraphicsContext;
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
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

/** Native JNI/FFM checks: concurrent driver rejection, unpublished outputs and retained teardown. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuPreparation", matches = "true")
@Timeout(25)
final class WGPUPreparationTest {
    private static final String SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;

    @ParameterizedTest
    @MethodSource("backends")
    void concurrentNativeFailuresDoNotPoisonSuccessfulJobsOrRendering(WGPUBackend backend) {
        Errors scenario = new Errors();
        run(scenario, 8, backend);
        assertEquals(12, scenario.ready.size());
        assertEquals(24, scenario.rejected);
        assertTrue(scenario.frames >= 3);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void concurrentTexturedPipelinesKeepResourceLayoutsIndependent(WGPUBackend backend) {
        ResourceLayouts scenario = new ResourceLayouts();
        run(scenario, 8, backend);
        assertEquals(scenario.jobs.length, scenario.settled);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void contextTeardownRetainsInputsAndDiscardsUnpublishedResults(WGPUBackend backend) {
        Teardown scenario = new Teardown(false);
        try {
            run(scenario, 2, backend);
            assertTrue(scenario.nativeContext.resourceDomain().isClosed());
            assertFalse(scenario.nativeContext.nativeDevice().isDisposed(), "Device died while source still runs");
            assertFalse(scenario.held.isDone());
            assertThrows(CancellationException.class, scenario.completed::finish);
            scenario.release.countDown();
            await(scenario.held);
            assertThrows(CancellationException.class, scenario.held::finish);
            scenario.held.dispose();
            scenario.completed.dispose();
            assertTrue(scenario.nativeContext.nativeDevice().isDisposed(), "Retained native device did not drain");
        } finally { scenario.release.countDown(); }
    }

    @ParameterizedTest
    @MethodSource("backends")
    void queuedCancellationDoesNotRunSourceGeneration(WGPUBackend backend) {
        Teardown scenario = new Teardown(true);
        try {
            run(scenario, 1, backend);
            scenario.release.countDown();
            await(scenario.held);
            await(scenario.completed);
            scenario.held.dispose();
            scenario.completed.dispose();
            assertEquals(0, scenario.queuedSources.get());
            assertTrue(scenario.nativeContext.nativeDevice().isDisposed());
        } finally { scenario.release.countDown(); }
    }

    private static void run(ApplicationAdapter scenario, int workers, WGPUBackend backend) {
        run(scenario, workers, backend, null);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void cacheWaitReleasesSingleWorkerAndTeardownDrainsLateStorageCompletion(WGPUBackend backend) {
        CacheWait scenario = new CacheWait();
        run(scenario, 1, backend, new ShaderArtifactCache(scenario.store));
        assertFalse(scenario.waiting.isDone());
        assertFalse(scenario.context.nativeDevice().isDisposed());
        scenario.read.complete(null);
        await(scenario.waiting);
        assertThrows(CancellationException.class, scenario.waiting::finish);
        scenario.waiting.dispose();
        assertTrue(scenario.context.nativeDevice().isDisposed());
    }

    private static void run(ApplicationAdapter scenario, int workers, WGPUBackend backend, ShaderArtifactCache cache) {
        List<String> services = ServiceLoader.load(WGPUPreparation.class).stream()
                .map(service -> service.type().getSimpleName()).toList();
        String bridge = System.getProperty("libfdx.test.wgpuBridge", "ffm");
        assertEquals(List.of("jni".equals(bridge) ? "WGPUJniPreparation" : "WGPUFfmPreparation"), services);
        System.out.println("WGPU preparation bridge=" + bridge + " backend=" + backend + " workers=" + workers);
        WGPUProvider provider = new WGPUProvider();
        if (dawn()) provider.loaderBackend(WGPULoaderBackend.DAWN);
        provider.configuration().backend(backend).offscreenReadback(true).vSync(false)
                .preparationWorkerLimit(workers).shaderCache(cache);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("WGPU async validation")
                .size(128, 128).visible(false).vSync(false).graphics(provider), scenario);
    }

    private static boolean dawn() { return Boolean.getBoolean("libfdx.test.nativeDawnPreparation"); }

    private static Stream<WGPUBackend> backends() {
        return dawn() ? Stream.of(WGPUBackend.D3D12) : Stream.of(WGPUBackend.VULKAN, WGPUBackend.D3D12);
    }

    private static void await(ShaderPreparationOperation operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!operation.isDone() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue(operation.isDone(), "Preparation did not drain after its source returned");
    }

    private static ShaderModuleDescriptor nativeSource(String label, String source) {
        ShaderTarget target = ShaderTarget.WGPU_WGSL;
        // Deliberately claim a nonexistent entry in the fixture so the native driver rejects it.
        ShaderReflection reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU).complete(true)
                .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("missingVertex", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build();
        ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(target.id(), target.format(), target.environment(),
                new ShaderStageArtifact[] { ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), source) },
                ShaderTranslatedInterface.identity(reflection, null), ShaderCompilerId.of("test.wgpu-native-errors"), "1", "");
        artifact = artifact.withVerification(ShaderTargetVerification.providerPipeline(target.environment(), null,
                artifact.compileCacheKey()));
        return ShaderModuleDescriptors.descriptor(artifact, label, source);
    }

    private static final class CacheWait extends ApplicationAdapter {
        final FdxFuture<byte[]> read = FdxFuture.pending();
        final AtomicInteger reads = new AtomicInteger();
        final ShaderCacheStore store = new ShaderCacheStore() {
            @Override public FdxFuture<byte[]> readAsync(String key) { reads.incrementAndGet(); return read; }
            @Override public FdxFuture<Void> writeAsync(String key, byte[] bytes) { return FdxFuture.completed(null); }
            @Override public FdxFuture<Void> removeAsync(String key) { return FdxFuture.completed(null); }
        };
        ShaderPreparationOperation waiting, other;
        WGPUContext context;
        Application application;
        long deadline;

        @Override public void create(Fdx fdx) {
            application = fdx.app();
            GraphicsContext graphics = fdx.graphics().main();
            context = graphics.as();
            assertTrue(graphics.device().shaderPreparationCapabilities().artifactCache());
            assertFalse(graphics.device().shaderPreparationCapabilities().pipelineCache());
            waiting = graphics.device().prepareRenderPipeline(packet(graphics,
                    ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("cache-wait", SHADER)), false));
            other = graphics.device().prepareRenderPipeline(packet(graphics,
                    ShaderModuleSource.fixed(nativeSource("cache-independent", SHADER)), false));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        }

        @Override public void render() {
            assertTrue(System.nanoTime() < deadline, "Cache wait occupied the single compiler worker");
            if (reads.get() == 1 && other.isDone()) {
                other.finish().dispose();
                other.dispose();
                other = null;
                assertFalse(waiting.isDone());
                application.requestExit();
            }
        }
    }

    private static ShaderPipelineRequest packet(GraphicsContext graphics, ShaderModuleSource source, boolean invalidEntry) {
        return new ShaderPipelineRequest(source, new RenderPipelineDescriptor().label("concurrent pipeline")
                .renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                .vertexEntryPoint(invalidEntry ? "missingVertex" : "vertexMain")
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }

    private static final class Errors extends ApplicationAdapter {
        final ShaderPreparationOperation[] jobs = new ShaderPreparationOperation[36];
        final List<ShaderPreparedResult> ready = new ArrayList<>();
        GraphicsContext graphics;
        Application application;
        int rejected, settled, frames;
        long deadline;

        @Override public void create(Fdx fdx) {
            graphics = fdx.graphics().main(); application = fdx.app();
            assertTrue(graphics.device().shaderPreparationCapabilities().runtimeNonblocking());
            assertEquals(dawn() ? ShaderPreparationCapabilities.Execution.NATIVE_ASYNC
                    : ShaderPreparationCapabilities.Execution.WORKERS,
                    graphics.device().shaderPreparationCapabilities().nativeExecution());
            for (int i = 0; i < jobs.length; i++) {
                ShaderModuleDescriptor source = nativeSource("native-job-" + i, i % 3 == 1 ? "invalid WGSL" : SHADER);
                jobs[i] = graphics.device().prepareRenderPipeline(packet(graphics, ShaderModuleSource.fixed(source), i % 3 == 2));
            }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        }

        @Override public void render() {
            assertTrue(System.nanoTime() < deadline, "Native jobs never settled");
            for (int i = 0; i < jobs.length; i++) {
                ShaderPreparationOperation job = jobs[i];
                if (job == null || !job.isDone()) continue;
                if (i % 3 == 0) ready.add(job.finish());
                else {
                    RuntimeException error = assertThrows(RuntimeException.class, job::finish);
                    if (dawn()) assertTrue(error.getMessage().contains(i % 3 == 1
                            ? "Could not create WGPU shader module" : "Dawn render pipeline failed"), error::getMessage);
                    else assertTrue(error.getMessage().contains(i % 3 == 1 ? "wgpuDeviceCreateShaderModule"
                            : "wgpuDeviceCreateRenderPipeline"), error::getMessage);
                    rejected++;
                }
                job.dispose(); jobs[i] = null; settled++;
            }
            var frame = graphics.currentFrame();
            var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                    .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
            try { if (!ready.isEmpty()) { pass.setPipeline(ready.get(0).pass().pipeline()); pass.draw(3, 1, 0, 0); } }
            finally { pass.end(); }
            frames++;
            if (settled == jobs.length && frames >= 3) {
                WGPUContext context = graphics.as();
                var pixels = context.readPixelsRgba8();
                int center = (frame.height() / 2 * frame.width() + frame.width() / 2) * 4;
                assertTrue((pixels.get(center + 1) & 255) > 240, "Triangle center must be green");
                assertTrue((pixels.get(center) & 255) < 10 && (pixels.get(1) & 255) < 10,
                        "Center red=" + (pixels.get(center) & 255) + ", corner green=" + (pixels.get(1) & 255));
                context.processEvents();
                System.out.println("WGPU_ASYNC_NATIVE_ERRORS ready=12 rejected=24 pixels=PASS frames=" + frames);
                application.requestExit();
            }
        }

        @Override public void dispose() {
            for (ShaderPreparationOperation job : jobs) if (job != null) job.cancel();
            for (ShaderPreparedResult result : ready) result.dispose();
        }
    }

    private static final class ResourceLayouts extends ApplicationAdapter {
        final ShaderPreparationOperation[] jobs = new ShaderPreparationOperation[64];
        Application application;
        int settled;
        long deadline;

        @Override public void create(Fdx fdx) {
            application = fdx.app();
            GraphicsContext graphics = fdx.graphics().main();
            for (int i = 0; i < jobs.length; i++) {
                String source = """
                        struct Params { tint: vec4f, }
                        @group(0) @binding(0) var image: texture_2d<f32>;
                        @group(0) @binding(1) var imageSampler: sampler;
                        @group(1) @binding(0) var<uniform> params: Params;
                        @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                            let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                            return vec4f(p[i], 0, 1);
                        }
                        @fragment fn fragmentMain() -> @location(0) vec4f {
                            return textureSample(image, imageSampler, vec2f(.5)) * params.tint *
                        """ + (i + 1) + ".0 / 64.0; }";
                jobs[i] = graphics.device().prepareRenderPipeline(packet(graphics,
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("resource-layout-" + i, source)), false));
            }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        }

        @Override public void render() {
            assertTrue(System.nanoTime() < deadline, "Textured pipeline jobs never settled");
            for (int i = 0; i < jobs.length; i++) {
                ShaderPreparationOperation job = jobs[i];
                if (job == null || !job.isDone()) continue;
                ShaderPreparedResult result = job.finish();
                try {
                    WGPURenderPipelineHandle pipeline = result.pass().pipeline().as();
                    assertEquals(1, pipeline.resourceBindings().uniformBufferCount());
                    assertEquals(1, pipeline.resourceBindings().sampledTextureCount());
                    assertEquals(1, pipeline.resourceBindings().samplerCount());
                } finally { result.dispose(); job.dispose(); }
                jobs[i] = null;
                settled++;
            }
            if (settled == jobs.length) {
                System.out.println("WGPU_ASYNC_RESOURCE_LAYOUTS ready=" + settled);
                application.requestExit();
            }
        }

        @Override public void dispose() {
            for (ShaderPreparationOperation job : jobs) if (job != null) job.cancel();
        }
    }

    private static final class Teardown extends ApplicationAdapter {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger queuedSources = new AtomicInteger();
        final boolean queued;
        ShaderPreparationOperation held, completed;
        WGPUContext nativeContext;
        Application application;
        long deadline;

        Teardown(boolean queued) { this.queued = queued; }

        @Override public void create(Fdx fdx) {
            application = fdx.app();
            GraphicsContext graphics = fdx.graphics().main(); nativeContext = graphics.as();
            Thread owner = Thread.currentThread();
            held = graphics.device().prepareRenderPipeline(packet(graphics,
                    ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                        assertNotSame(owner, Thread.currentThread(), "Source generation ran on the application thread");
                        started.countDown();
                        try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                        catch (InterruptedException error) { throw new AssertionError(error); }
                        return nativeSource("held", SHADER);
                    }), false));
            completed = graphics.device().prepareRenderPipeline(packet(graphics,
                    ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                        queuedSources.incrementAndGet();
                        return nativeSource("completed", SHADER);
                    }), false));
            if (queued) completed.cancel();
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        }

        @Override public void render() {
            assertTrue(System.nanoTime() < deadline);
            if (started.getCount() == 0 && (queued || completed.isDone())) application.requestExit();
        }
    }
}
