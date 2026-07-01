package io.github.libfdx.samples.ecs.platformer.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.ecs.platformer.EcsPlatformerApplication;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Launches the ECS platformer desktop entry point.
 *
 * @author xpenatan
 */
public final class EcsPlatformerDesktopLauncher {
    private static final String LAUNCH_PROPERTIES_PATH = "libfdx-desktop-launch.properties";
    private static final Properties LAUNCH_PROPERTIES = loadLaunchProperties();

    private EcsPlatformerDesktopLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String graphics = graphicsName(args);
        boolean maximized = Boolean.parseBoolean(option(args, "--maximized=",
                System.getProperty("libfdx.sample.maximized", "true")));
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx ECS Platformer - " + graphicsDisplayName(args, graphics))
                .size(960, 540)
                .maximized(maximized)
                .vSync(true)
                .foregroundFps(60)
                .graphics(graphicsProvider(graphics));

        new DesktopApplicationBackend().start(config, new EcsPlatformerApplication(exitAfterFrames(args)));
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
        return option(args, "--graphics=",
                System.getProperty("libfdx.sample.graphics", launchProperty("graphics", "wgpu")));
    }

    private static String graphicsDisplayName(String[] args, String graphics) {
        String configured = option(args, "--graphics-label=",
                System.getProperty("libfdx.sample.graphicsLabel", launchProperty("graphicsLabel", null)));
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

    private static String launchProperty(String name, String fallback) {
        String value = LAUNCH_PROPERTIES.getProperty(name);
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        return fallback;
    }

    private static Properties loadLaunchProperties() {
        Properties properties = new Properties();
        try (InputStream stream = EcsPlatformerDesktopLauncher.class.getClassLoader()
                .getResourceAsStream(LAUNCH_PROPERTIES_PATH)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Launch properties are optional outside release jars.
        }
        return properties;
    }
}
