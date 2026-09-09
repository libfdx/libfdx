package io.github.libfdx.backend.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real Vulkan allocation/merge after the backend's owner context has been disposed. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativePreparationLifecycle", matches = "true")
@Timeout(30)
final class DesktopVulkanCacheMergeLifecycleTest {
    @Test void pendingNativeMergeRetainsDeviceBeyondOwnerAndStoreDisposal() throws Exception {
        Files.createDirectories(Path.of("build"));
        Path directory = Files.createTempDirectory(Path.of("build"), "vulkan-merge-lifecycle-");
        DesktopShaderCacheStore store = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
        HeldMerge held = new HeldMerge(store);
        try {
            run(new ShaderArtifactCache(store), null, "0.3");
            await(store.flushAsync());
            ShaderArtifactCache cache = new ShaderArtifactCache(held);
            run(cache, held, "0.6");
            assertEquals(0, held.entered.getCount());
            assertFalse(held.finished.isDone(), "The storage transaction must outlive owner disposal");
            store.dispose(); // Accepted merge must still run and release its retained native device.
            held.release.countDown();
            await(held.finished);
            assertEquals(1, cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).writes());
            assertEquals(0, cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).storageFailures());
            DesktopShaderCacheStore fresh = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
            try {
                ShaderArtifactCache restored = new ShaderArtifactCache(fresh);
                run(restored, null, "0.3");
                await(fresh.flushAsync());
                assertTrue(restored.metrics(ShaderCacheLayer.DRIVER_PIPELINE).hits() > 0);
                var metrics = restored.metrics(ShaderCacheLayer.DRIVER_PIPELINE);
                if (metrics.pipelineFeedbacks() > 0) assertEquals(1, metrics.pipelineCacheHits());
            } finally { fresh.dispose(); }
        } finally { held.release.countDown(); store.dispose(); }
    }

    private static void run(ShaderArtifactCache cache, HeldMerge held, String color) {
        DesktopVulkanProvider provider = new DesktopVulkanProvider(); provider.configuration().shaderCache(cache);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().size(64, 64).visible(false)
                .vSync(false).graphics(provider), new ApplicationAdapter() {
            Application application;
            ShaderPreparationOperation operation;
            boolean ready;
            long deadline;
            @Override public void create(Fdx fdx) {
                application = fdx.app(); deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                operation = fdx.graphics().main().device().prepareRenderPipeline(new ShaderPipelineRequest(
                        ShaderModuleDescriptor.wgsl("merge lifetime", """
                            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                                return vec4f(f32(i) * 0.1, 0.0, 0.0, 1.0);
                            }
                            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(%s, 0.0, 0.0, 1.0); }
                            """.formatted(color)), new RenderPipelineDescriptor().colorFormat(fdx.graphics().main().surfaceFormat()),
                        ShaderPassId.FORWARD, 0));
            }
            @Override public void render() {
                assertTrue(System.nanoTime() < deadline, "Vulkan preparation/merge did not reach the barrier");
                if (!ready && operation.isDone()) {
                    operation.finish().dispose(); operation.dispose(); ready = true;
                }
                if (ready && (held == null || held.entered.getCount() == 0)) application.requestExit();
            }
            @Override public void dispose() { if (!ready) operation.cancel(); }
        });
    }

    private static <T> T await(FdxFuture<T> future) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        future.onSuccess(value -> done.countDown()).onFailure(error -> done.countDown());
        assertTrue(done.await(10, TimeUnit.SECONDS)); return future.get();
    }

    private static final class HeldMerge implements ShaderCacheStore {
        final DesktopShaderCacheStore delegate;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FdxFuture<Void> finished;
        HeldMerge(DesktopShaderCacheStore delegate) { this.delegate = delegate; }
        @Override public FdxFuture<byte[]> readAsync(String key) { return delegate.readAsync(key); }
        @Override public FdxFuture<Void> writeAsync(String key, byte[] bytes) { return delegate.writeAsync(key, bytes); }
        @Override public FdxFuture<Void> removeAsync(String key) { return delegate.removeAsync(key); }
        @Override public boolean supportsAtomicUpdate() { return true; }
        @Override public FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> update) {
            finished = delegate.updateAsync(key, current -> {
                assertNotNull(current, "Prime the stored cache so this operation calls the native merger");
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return update.apply(current);
            });
            return finished;
        }
    }
}
