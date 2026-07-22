package io.github.libfdx.samples.g2d.spritemovement.android;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.ecs.tooling.EcsProjectApplication;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;

/**
 * Base Android activity for the 2D Sprite Movement sample.
 *
 * @author xpenatan
 */
public class SpriteMovementAndroidActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        AndroidApplicationConfig config = new AndroidApplicationConfig()
                .title("libfdx 2D Sprite Movement - " + graphicsDisplayName())
                .size(640, 480)
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
        return new EcsProjectApplication(new SpriteMovementProject());
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
