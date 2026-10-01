package io.github.libfdx.benchmark.desktopgraal;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.benchmark.graphics.SpriteBatchStressBenchmark;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class DesktopGraalBenchmarkLauncher {
    private DesktopGraalBenchmarkLauncher() {
    }

    public static void main(String[] args) {
        // The image is compiled against LWJGL's JNI classes, with multi-release entries disabled.
        System.setProperty("jdk.util.jar.enableMultiRelease", "false");
        System.setProperty("org.lwjgl.system.stackSize", System.getProperty("org.lwjgl.system.stackSize", "1024"));
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
        forwardBenchmarkProperty(args, "sprites", null);
        forwardBenchmarkProperty(args, "capture", null);
        forwardBenchmarkProperty(args, "cpuDiagnostic", null);
        forwardBenchmarkProperty(args, "frameProfile", null);
        forwardBenchmarkProperty(args, "warmupSeconds", "2");
        forwardBenchmarkProperty(args, "device", "unspecified");
        forwardBenchmarkProperty(args, "driver", "unspecified");
        forwardBenchmarkProperty(args, "revision", "unspecified");

        System.out.println("[info] DesktopGraalBenchmarkLauncher starting " + benchmarkName
                + " with " + graphics.label
                + ", seconds=" + seconds
                + ", vSync=" + vSync
                + ", foregroundFps=" + foregroundFps
                + ", visible=" + visible);

        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Benchmark: " + benchmarkName + " - " + graphics.label)
                .size(640, 480)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(foregroundFps)
                .graphics(graphics.provider);

        new DesktopApplicationBackend().start(config, benchmark(benchmarkName, seconds, result));
    }

    private static GraphicsSelection graphicsSelection(String value, boolean vSync) {
        String normalized = value != null ? value.trim().toLowerCase() : "";
        if ("gl".equals(normalized) || "opengl".equals(normalized)) {
            return new GraphicsSelection("gl", "GL desktop_graal", new DesktopOpenGLProvider());
        }
        if (normalized.length() == 0 || "vk".equals(normalized) || "vulkan".equals(normalized)) {
            DesktopVulkanProvider provider = new DesktopVulkanProvider().vSync(vSync).framesInFlight(3);
            if (!vSync) {
                provider.configuration().preferMailboxPresentMode(false);
            }
            return new GraphicsSelection("vulkan", "Vulkan desktop_graal", provider);
        }
        throw new FdxException("Unknown desktop_graal benchmark graphics API: " + value
                + " (expected gl or vulkan)");
    }

    private static ApplicationListener benchmark(String benchmarkName, String seconds, String result) {
        String normalized = benchmarkName != null ? benchmarkName.trim() : "";
        if (normalized.length() == 0 || SpriteBatchStressBenchmark.NAME.equals(normalized)) {
            return new SpriteBatchStressBenchmark(exitAfterNanos(seconds), result);
        }
        if (SpriteBatchStressBenchmark.LIBFDX_NAME.equals(normalized)) {
            return new SpriteBatchStressBenchmark(exitAfterNanos(seconds), result, true);
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

    private static void forwardBenchmarkProperty(String[] args, String name, String defaultValue) {
        String property = "libfdx.benchmark." + name;
        String value = option(args, name, System.getProperty(property, defaultValue));
        if (value != null) {
            System.setProperty(property, value);
        }
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

