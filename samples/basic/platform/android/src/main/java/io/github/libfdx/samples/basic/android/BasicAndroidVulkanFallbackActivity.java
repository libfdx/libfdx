package io.github.libfdx.samples.basic.android;

import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class BasicAndroidVulkanFallbackActivity extends BasicAndroidActivity {
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
