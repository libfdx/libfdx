package io.github.libfdx.samples.shadergraph.desktop;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleApplication;
import io.github.libfdx.samples.shadergraph.editor.ShaderGraphEditorSampleApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Launches the headless or optional-editor shader graph desktop sample.
 */
public final class ShaderGraphSampleDesktopLauncher {
    private static final String LAUNCH_PROPERTIES_PATH =
            "libfdx-desktop-launch.properties";
    private static final Properties LAUNCH_PROPERTIES =
            loadLaunchProperties();

    private ShaderGraphSampleDesktopLauncher() {
    }

    /**
     * Runs the desktop sample.
     *
     * @param args optional launch overrides
     */
    public static void main(String[] args) {
        String graphics = graphicsName(args);
        boolean editor = Boolean.parseBoolean(option(args,
                "--editor=", System.getProperty(
                        "libfdx.sample.editor", "false")));
        boolean maximized = Boolean.parseBoolean(option(args,
                "--maximized=", System.getProperty(
                        "libfdx.sample.maximized", "false")));
        boolean visible = Boolean.parseBoolean(option(args,
                "--visible=", System.getProperty(
                        "libfdx.sample.visible", "true")));
        boolean vSync = Boolean.parseBoolean(option(args,
                "--vsync=", System.getProperty(
                        "libfdx.sample.vsync", "true")));
        long frames = exitAfterFrames(args);
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Shader Graph "
                        + (editor ? "Editor" : "Sample")
                        + " - " + graphicsDisplayName(args, graphics))
                .size(1280, 720)
                .maximized(maximized)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(visible ? 60 : 0)
                .graphics(graphicsProvider(graphics, vSync));
        ApplicationListener listener = editor
                ? new ShaderGraphEditorSampleApplication(frames)
                : new ShaderGraphSampleApplication(frames);
        new DesktopApplicationBackend().start(config, listener);
    }

    private static GraphicsAttachmentProvider graphicsProvider(
            String graphics, boolean vSync) {
        if ("gl".equalsIgnoreCase(graphics)
                || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics)
                || "vk".equalsIgnoreCase(graphics)) {
            return new DesktopVulkanProvider().vSync(vSync);
        }
        if (isD3D12(graphics)) {
            return new D3D12Provider().vSync(vSync);
        }
        return new WGPUProvider().vSync(vSync);
    }

    private static String graphicsName(String[] args) {
        return option(args, "--graphics=",
                System.getProperty("libfdx.sample.graphics",
                        launchProperty("graphics", "wgpu")));
    }

    private static String graphicsDisplayName(String[] args,
            String graphics) {
        String configured = option(args, "--graphics-label=",
                System.getProperty("libfdx.sample.graphicsLabel",
                        launchProperty("graphicsLabel", null)));
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if ("gl".equalsIgnoreCase(graphics)
                || "opengl".equalsIgnoreCase(graphics)) {
            return "GL";
        }
        if ("vulkan".equalsIgnoreCase(graphics)
                || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        return isD3D12(graphics) ? "Direct3D 12" : "WGPU";
    }

    private static boolean isD3D12(String graphics) {
        return "d3d12".equalsIgnoreCase(graphics)
                || "direct3d12".equalsIgnoreCase(graphics)
                || "directx12".equalsIgnoreCase(graphics)
                || "dx12".equalsIgnoreCase(graphics);
    }

    private static long exitAfterFrames(String[] args) {
        String value = option(args, "--exit-after-frames=",
                System.getProperty(
                        "libfdx.sample.exitAfterFrames"));
        return value != null && !value.isBlank()
                ? Long.parseLong(value) : 0L;
    }

    private static String option(String[] args, String prefix,
            String fallback) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) {
                    return arg.substring(prefix.length());
                }
            }
        }
        return fallback;
    }

    private static String launchProperty(String name,
            String fallback) {
        String value = LAUNCH_PROPERTIES.getProperty(name);
        return value != null && !value.isBlank()
                ? value.trim() : fallback;
    }

    private static Properties loadLaunchProperties() {
        Properties properties = new Properties();
        try (InputStream stream =
                ShaderGraphSampleDesktopLauncher.class
                        .getClassLoader().getResourceAsStream(
                                LAUNCH_PROPERTIES_PATH)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Launch properties are optional outside packaged applications.
        }
        return properties;
    }
}
