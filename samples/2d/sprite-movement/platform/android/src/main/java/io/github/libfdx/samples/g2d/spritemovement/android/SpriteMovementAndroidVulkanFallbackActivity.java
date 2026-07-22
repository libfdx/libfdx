package io.github.libfdx.samples.g2d.spritemovement.android;

import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Runs the 2D Sprite Movement sample with Vulkan and an OpenGL ES fallback.
 *
 * @author xpenatan
 */
public final class SpriteMovementAndroidVulkanFallbackActivity extends SpriteMovementAndroidActivity {
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
