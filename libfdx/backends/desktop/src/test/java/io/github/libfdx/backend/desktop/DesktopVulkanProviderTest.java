package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider.VulkanResourceDomain;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.TextureFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DesktopVulkanProviderTest {
    @Test
    void lossRejectsNewWorkButRetainsNativeOwnershipUntilEveryUserReturns() {
        var domain = new VulkanResourceDomain();
        AtomicInteger releases = new AtomicInteger();
        domain.setNativeRelease(releases::incrementAndGet);
        domain.retainContext(); domain.retainContext(); domain.retainPreparation(); domain.retainCacheSave();
        assertThrows(GraphicsContextLostException.class, () -> domain.check(VK_ERROR_DEVICE_LOST, "worker"));
        domain.observeResult(VK_SUCCESS); // A later successful result cannot revive this device.
        assertTrue(domain.isLost()); assertFalse(domain.isClosed());
        assertThrows(GraphicsContextLostException.class, domain::requireUsable);
        assertThrows(GraphicsContextLostException.class, domain::retainContext);
        assertThrows(GraphicsContextLostException.class, domain::retainPreparation);
        assertThrows(GraphicsContextLostException.class, domain::retainCacheSave);
        domain.releaseContext(); domain.releaseContext();
        assertTrue(domain.isClosed()); assertEquals(0, releases.get());
        domain.releasePreparation(); assertEquals(0, releases.get());
        domain.releasePreparation(); assertEquals(1, releases.get());
        domain.observeResult(VK_ERROR_DEVICE_LOST); assertEquals(1, releases.get());
    }
    @Test
    void closingAllWindowsDefersNativeReleaseUntilTheWorkerReturns() throws Exception {
        var domain = new DesktopVulkanProvider.VulkanResourceDomain();
        AtomicInteger releases = new AtomicInteger();
        domain.setNativeRelease(releases::incrementAndGet);
        domain.retainContext(); domain.retainContext();
        domain.retainPreparation();
        CountDownLatch finish = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try { finish.await(); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            domain.releasePreparation();
        });
        worker.start();
        try {
            domain.releaseContext();
            assertFalse(domain.isClosed());
            domain.releaseContext();
            assertTrue(domain.isClosed());
            assertEquals(0, releases.get());
            assertThrows(FdxException.class, domain::retainPreparation);
            assertThrows(FdxException.class, domain::retainContext);
        } finally { finish.countDown(); worker.join(TimeUnit.SECONDS.toMillis(5)); }
        assertFalse(worker.isAlive());
        assertEquals(1, releases.get());
        assertThrows(FdxException.class, domain::releasePreparation);
        assertEquals(1, releases.get());
    }

    @Test
    void completedWorkersDoNotDestroyADeviceWithAnOpenWindow() {
        var domain = new DesktopVulkanProvider.VulkanResourceDomain();
        AtomicInteger releases = new AtomicInteger();
        domain.setNativeRelease(releases::incrementAndGet);
        domain.retainContext(); domain.retainPreparation(); domain.retainPreparation();
        domain.releasePreparation(); domain.releasePreparation();
        assertEquals(0, releases.get());
        domain.releaseContext();
        assertEquals(1, releases.get());
    }

    @Test
    void cacheSaveCanRetainAnActiveJobAfterOwnerTeardown() {
        var domain = new DesktopVulkanProvider.VulkanResourceDomain();
        AtomicInteger releases = new AtomicInteger();
        domain.setNativeRelease(releases::incrementAndGet);
        domain.retainContext(); domain.retainPreparation();
        domain.releaseContext();
        domain.retainCacheSave();
        domain.releasePreparation();
        assertEquals(0, releases.get());
        domain.releasePreparation();
        assertEquals(1, releases.get());
        assertThrows(FdxException.class, domain::retainCacheSave);
    }

    @Test
    void advertisesAlphaBlendControlWithoutCompletePipelineState() {
        assertTrue(DesktopVulkanProvider.CAPABILITIES.supports(
                GraphicsFeature.ALPHA_BLEND_CONTROL));
        assertFalse(DesktopVulkanProvider.CAPABILITIES.supports(
                GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE));
    }

    @Test
    void selectsBlendEnableFromTheColorTarget() {
        assertFalse(DesktopVulkanProvider.blendEnabled(
                ColorTargetState.opaque(TextureFormat.RGBA8_UNORM)));
        assertTrue(DesktopVulkanProvider.blendEnabled(
                ColorTargetState.alpha(TextureFormat.RGBA8_UNORM)));
    }
}
