package io.github.libfdx.samples.basic.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.basic.BasicApplication;

public final class BasicDesktopLauncher {
    private BasicDesktopLauncher() {
    }

    public static void main(String[] args) {
        String graphics = graphicsName(args);
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Basic - " + graphicsDisplayName(args, graphics))
                .size(640, 480)
                .vSync(true)
                .foregroundFps(60)
                .graphics(graphicsProvider(graphics));

        new DesktopApplicationBackend().start(config, new BasicApplication(exitAfterFrames(args)));
    }

    private static GraphicsAttachmentProvider graphicsProvider(String graphics) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return new DesktopVulkanProvider();
        }
        return new WGPUProvider();
    }

    private static String graphicsName(String[] args) {
        return option(args, "--graphics=", System.getProperty("libfdx.sample.graphics", "wgpu"));
    }

    private static String graphicsDisplayName(String[] args, String graphics) {
        String configured = option(args, "--graphics-label=", System.getProperty("libfdx.sample.graphicsLabel"));
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return "GL";
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        return "WGPU";
    }

    private static long exitAfterFrames(String[] args) {
        String value = option(args, "--exit-after-frames=", System.getProperty("libfdx.sample.exitAfterFrames"));
        if (value != null) {
            return Long.parseLong(value);
        }
        return 0L;
    }

    private static String option(String[] args, String prefix, String fallback) {
        if (args == null) {
            return fallback;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }
}
