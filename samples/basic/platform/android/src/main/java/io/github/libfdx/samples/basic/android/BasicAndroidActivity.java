package io.github.libfdx.samples.basic.android;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.basic.BasicApplication;

public class BasicAndroidActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        AndroidApplicationConfig config = new AndroidApplicationConfig()
                .title("libfdx Basic - " + graphicsDisplayName())
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
        return new BasicApplication();
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
