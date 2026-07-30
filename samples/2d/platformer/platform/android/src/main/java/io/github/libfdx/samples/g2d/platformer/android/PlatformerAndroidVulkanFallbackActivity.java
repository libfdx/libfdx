package io.github.libfdx.samples.g2d.platformer.android;

import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Represents a platformer Android Vulkan fallback activity.
 *
 * @author xpenatan
 */
public final class PlatformerAndroidVulkanFallbackActivity extends PlatformerAndroidActivity {
    @Override
    protected String graphicsName() {
        return "vulkan";
    }

    @Override
    protected GraphicsAttachmentProvider[] fallbackGraphicsProviders() {
        return new GraphicsAttachmentProvider[] {new AndroidGlesProvider()};
    }

    @Override
    protected String graphicsDisplayName() {
        return "Vulkan JNI -> OpenGL ES fallback";
    }
}
