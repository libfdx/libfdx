package io.github.libfdx.backend.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in because this check needs actual desktop drivers and hidden native windows. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativePreparationLifecycle", matches = "true")
@Timeout(20)
final class DesktopPreparationLifecycleTest {
    static Stream<Arguments> providers() {
        return Stream.of(Arguments.of("D3D12", new D3D12Provider()),
                Arguments.of("Vulkan", new DesktopVulkanProvider()),
                Arguments.of("GL", new DesktopOpenGLProvider()));
    }

    static Stream<Arguments> cacheProviders() {
        return Stream.of(Arguments.of("D3D12", new D3D12Provider()),
                Arguments.of("Vulkan", new DesktopVulkanProvider()),
                Arguments.of("GL", new DesktopOpenGLProvider()),
                Arguments.of("GL program binary", new DesktopOpenGLProvider()));
    }

    @ParameterizedTest(name = "{0}: pending cache reads survive owner teardown")
    @MethodSource("cacheProviders")
    void closeDeviceDuringCacheLookup(String name, GraphicsAttachmentProvider provider) {
        ControlledPreparation scenario = new ControlledPreparation(true, provider instanceof DesktopOpenGLProvider && !name.equals("GL program binary")
                ? ShaderPreparationPhase.TRANSLATION : ShaderPreparationPhase.CACHE_LOOKUP);
        ShaderArtifactCache artifacts = new ShaderArtifactCache(scenario.heldCache);
        if (provider instanceof D3D12Provider d3d12) d3d12.configuration().shaderCache(artifacts);
        else if (provider instanceof DesktopVulkanProvider vulkan) vulkan.configuration().shaderCache(artifacts);
        else ((DesktopOpenGLProvider) provider).configuration().shaderCache(artifacts);
        try {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("cache lifecycle " + name)
                    .size(64, 64).visible(false).vSync(false).graphics(provider), scenario);
            assertEquals(0, scenario.heldCache.started.getCount());
            assertFalse(scenario.drained.isDone(), "Pending cache continuations lost their retained device");
            scenario.heldCache.release();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!scenario.drained.isDone() && System.nanoTime() < deadline) {
                scenario.shaders.update();
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertTrue(scenario.drained.isDone());
            scenario.drained.get();
        } finally { scenario.release.countDown(); scenario.heldCache.release(); }
    }

    @ParameterizedTest(name = "{0}: pending source survives device teardown and drains without publication")
    @MethodSource("providers")
    void closeDeviceWhileWorkerOwnsInputs(String name, GraphicsAttachmentProvider provider) {
        ControlledPreparation scenario = new ControlledPreparation();
        try {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("preparation lifecycle " + name)
                    .size(64, 64).visible(false).vSync(false).graphics(provider), scenario);
            // The backend has destroyed its graphics context/window while the generator still owns its inputs.
            assertEquals(0, scenario.started.getCount());
            assertFalse(scenario.drained.isDone(), "Active worker inputs were released before the generator returned");
            scenario.release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!scenario.drained.isDone() && System.nanoTime() < deadline) {
                scenario.shaders.update();
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertTrue(scenario.drained.isDone(), "Cancelled native preparation did not drain after device teardown");
            scenario.drained.get();
        } finally { scenario.release.countDown(); }
    }

    private static final class ControlledPreparation extends ApplicationAdapter {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        final HeldCache heldCache;
        final ShaderPreparationPhase heldPhase;
        volatile ShaderPreparationOperation operation;
        ShaderPreparation shaders;
        ShaderPreparationScope scope;
        FdxFuture<Void> drained;
        Application application;
        long deadline;

        ControlledPreparation() { this(false, ShaderPreparationPhase.CACHE_LOOKUP); }
        ControlledPreparation(boolean holdCache, ShaderPreparationPhase heldPhase) {
            heldCache = holdCache ? new HeldCache(this) : null;
            this.heldPhase = heldPhase;
        }

        @Override public void create(Fdx fdx) {
            application = fdx.app();
            GraphicsDevice device = fdx.graphics().main().device();
            assertTrue(device.shaderPreparationCapabilities().runtimeNonblocking());
            shaders = new ShaderPreparation(fdx.graphics().main());
            scope = shaders.createScope("controlled worker");
            RenderTargetLayout target = RenderTargetLayout.color(fdx.graphics().main().surfaceFormat());
            ShaderModuleSource source = ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                started.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS), "Worker release was never signalled"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                return ShaderModuleDescriptor.wgsl("cancelled source", """
                        @vertex fn vertexMain(@builtin(vertex_index) index: u32) -> @builtin(position) vec4f {
                            return vec4f(f32(index), 0.0, 0.0, 1.0);
                        }
                        @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(1.0); }
                        """);
            });
            ShaderPipelineRequest packet = new ShaderPipelineRequest(source,
                    new RenderPipelineDescriptor().renderTargetLayout(target), ShaderPassId.FORWARD, 0);
            ShaderProvider shaderProvider = new ShaderProvider() {
                @Override public GraphicsDevice preparationDevice() { return device; }
                @Override public boolean supports(ShaderRequest request) { return true; }
                @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
                    operation = device.prepareRenderPipeline(packet);
                    return operation;
                }
            };
            scope.include(shaderProvider, ShaderRequest.builder(ShaderPassId.FORWARD)
                    .renderPass(RenderPassCompatibility.layout(target)).build());
            shaders.prepareAsync(scope.seal());
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        }

        @Override public void render() {
            shaders.update();
            if (heldCache != null) {
                if (started.getCount() == 0) release.countDown();
                if (heldCache.started.getCount() == 0) application.requestExit();
                else {
                    if (operation != null && operation.isDone()) {
                        operation.finish().dispose();
                        fail("Preparation completed without reaching the held cache lookup");
                    }
                    assertTrue(System.nanoTime() < deadline, () -> "The provider never reached cache lookup; phase="
                            + (operation == null ? "not submitted" : operation.phase()));
                }
            } else if (started.getCount() == 0) application.requestExit();
            else assertTrue(System.nanoTime() < deadline, "The provider never started the source worker");
        }

        @Override public void dispose() {
            if (scope != null) scope.dispose();
            if (shaders != null) { drained = shaders.disposeAsync(); shaders.update(); }
        }
    }

    private static final class HeldCache implements ShaderCacheStore {
        final CountDownLatch started = new CountDownLatch(1);
        final ControlledPreparation scenario;
        final List<FdxFuture<byte[]>> reads = new ArrayList<>();
        final Map<String, byte[]> entries = new HashMap<>();
        boolean released;
        HeldCache(ControlledPreparation scenario) { this.scenario = scenario; }
        @Override public synchronized FdxFuture<byte[]> readAsync(String key) {
            if (!released && scenario.operation != null && scenario.operation.phase() == scenario.heldPhase) {
                FdxFuture<byte[]> pending = FdxFuture.pending(); reads.add(pending); started.countDown(); return pending;
            }
            byte[] bytes = entries.get(key);
            return FdxFuture.completed(bytes == null ? null : bytes.clone());
        }
        void release() {
            List<FdxFuture<byte[]>> pending;
            synchronized (this) { released = true; pending = new ArrayList<>(reads); reads.clear(); }
            for (var read : pending) read.complete(null);
        }
        @Override public synchronized FdxFuture<Void> writeAsync(String key, byte[] bytes) {
            entries.put(key, bytes.clone()); return FdxFuture.completed(null);
        }
        @Override public synchronized FdxFuture<Void> removeAsync(String key) {
            entries.remove(key); return FdxFuture.completed(null);
        }
        @Override public boolean supportsAtomicUpdate() { return true; }
        @Override public synchronized FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> update) {
            byte[] bytes = entries.get(key);
            entries.put(key, update.apply(bytes == null ? null : bytes.clone()).clone());
            return FdxFuture.completed(null);
        }
    }
}
