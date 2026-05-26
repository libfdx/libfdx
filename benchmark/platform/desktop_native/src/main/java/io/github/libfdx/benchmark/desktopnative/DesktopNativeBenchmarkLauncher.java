package io.github.libfdx.benchmark.desktopnative;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopnative.DesktopNativeApplicationBackend;
import io.github.libfdx.backend.desktopnative.DesktopNativeApplicationConfig;
import io.github.libfdx.backend.desktopnative.DesktopNativeOpenGLProvider;
import io.github.libfdx.backend.desktopnative.DesktopNativeVulkanProvider;
import io.github.libfdx.benchmark.graphics.SpriteBatchStressBenchmark;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class DesktopNativeBenchmarkLauncher {
    private DesktopNativeBenchmarkLauncher() {
    }

    public static void main(String[] args) {
        String benchmarkName = option(args, "benchmark",
                System.getProperty("libfdx.benchmark.name", SpriteBatchStressBenchmark.NAME));
        String graphicsApi = option(args, "graphics", System.getProperty("libfdx.benchmark.graphics", "vulkan"));
        String seconds = option(args, "seconds", System.getProperty("libfdx.benchmark.seconds", "8"));
        String result = option(args, "result", System.getProperty("libfdx.benchmark.result"));
        boolean visible = Boolean.parseBoolean(option(args, "visible",
                System.getProperty("libfdx.benchmark.visible", "true")));
        boolean vSync = Boolean.parseBoolean(option(args, "vsync",
                System.getProperty("libfdx.benchmark.vsync", "false")));
        int foregroundFps = parseInt(option(args, "foregroundFps",
                System.getProperty("libfdx.benchmark.foregroundFps", "0")), 0);

        GraphicsSelection graphics = graphicsSelection(graphicsApi, vSync);

        System.setProperty("libfdx.benchmark.name", benchmarkName);
        System.setProperty("libfdx.benchmark.graphics", graphics.id);
        System.setProperty("libfdx.benchmark.graphicsLabel", graphics.label);
        System.setProperty("libfdx.benchmark.seconds", seconds);
        if (result != null && result.trim().length() > 0) {
            System.setProperty("libfdx.benchmark.result", result);
        }
        System.setProperty("libfdx.benchmark.visible", String.valueOf(visible));
        System.setProperty("libfdx.benchmark.vsync", String.valueOf(vSync));
        System.setProperty("libfdx.benchmark.foregroundFps", String.valueOf(foregroundFps));

        System.out.println("[info] DesktopNativeBenchmarkLauncher starting " + benchmarkName
                + " with " + graphics.label
                + ", seconds=" + seconds
                + ", vSync=" + vSync
                + ", foregroundFps=" + foregroundFps
                + ", visible=" + visible);

        DesktopNativeApplicationConfig config = new DesktopNativeApplicationConfig()
                .title("libfdx Benchmark: " + benchmarkName + " - " + graphics.label)
                .size(640, 480)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(foregroundFps)
                .graphics(graphics.provider);

        new DesktopNativeApplicationBackend().start(config, benchmark(benchmarkName, seconds, result));
    }

    private static GraphicsSelection graphicsSelection(String value, boolean vSync) {
        String normalized = value != null ? value.trim().toLowerCase() : "";
        if ("gl".equals(normalized) || "opengl".equals(normalized)) {
            return new GraphicsSelection("gl", "GL desktop_native", new DesktopNativeOpenGLProvider());
        }
        if (normalized.length() == 0 || "vk".equals(normalized) || "vulkan".equals(normalized)) {
            DesktopNativeVulkanProvider provider = new DesktopNativeVulkanProvider().vSync(vSync).framesInFlight(3);
            if (!vSync) {
                provider.configuration().preferMailboxPresentMode(false);
            }
            return new GraphicsSelection("vulkan", "Vulkan desktop_native", provider);
        }
        throw new FdxException("Unknown desktop_native benchmark graphics API: " + value
                + " (expected gl or vulkan)");
    }

    private static ApplicationListener benchmark(String benchmarkName, String seconds, String result) {
        String normalized = benchmarkName != null ? benchmarkName.trim() : "";
        if (normalized.length() == 0 || SpriteBatchStressBenchmark.NAME.equals(normalized)) {
            return new SpriteBatchStressBenchmark(exitAfterNanos(seconds), result);
        }
        throw new FdxException("Unknown benchmark: " + benchmarkName);
    }

    private static long exitAfterNanos(String secondsValue) {
        if (secondsValue == null || secondsValue.trim().length() == 0) {
            return 0L;
        }
        double seconds = Double.parseDouble(secondsValue.trim());
        if (seconds <= 0.0) {
            return 0L;
        }
        double nanos = seconds * 1000000000.0;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) nanos;
    }

    private static String option(String[] args, String name, String defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        String prefix = "--" + name + "=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static final class GraphicsSelection {
        final String id;
        final String label;
        final GraphicsAttachmentProvider provider;

        GraphicsSelection(String id, String label, GraphicsAttachmentProvider provider) {
            this.id = id;
            this.label = label;
            this.provider = provider;
        }
    }
}
