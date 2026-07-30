package io.github.libfdx.samples.g2d.platformer.android;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.g2d.platformer.PlatformerApplication;

/**
 * Represents a platformer Android activity.
 *
 * @author xpenatan
 */
public class PlatformerAndroidActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        AndroidApplicationConfig config = new AndroidApplicationConfig()
                .title("libfdx Platformer - " + graphicsDisplayName())
                .size(960, 540)
                .vSync(true)
                .foregroundFps(60)
                .graphics(graphicsProvider());
        GraphicsAttachmentProvider[] fallbackGraphics = fallbackGraphicsProviders();
        if (fallbackGraphics.length > 0) {
            config.fallbackGraphics(fallbackGraphics);
        }
        return config;
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        return new PlatformerApplication();
    }

    private GraphicsAttachmentProvider graphicsProvider() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return new AndroidGlesProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return new AndroidVulkanProvider();
        }
        return new WGPUProvider();
    }

    protected String graphicsName() {
        return "wgpu";
    }

    protected GraphicsAttachmentProvider[] fallbackGraphicsProviders() {
        return new GraphicsAttachmentProvider[0];
    }

    protected String graphicsDisplayName() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return "OpenGL ES";
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return "Vulkan JNI";
        }
        return "WGPU JNI";
    }
}
