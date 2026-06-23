package io.github.libfdx.benchmark.desktop;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.benchmark.graphics.SpriteBatchStressBenchmark;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;

public final class DesktopBenchmarkLauncher {
    private DesktopBenchmarkLauncher() {
    }

    public static void main(String[] args) {
        String benchmarkName = System.getProperty("libfdx.benchmark.name", SpriteBatchStressBenchmark.NAME);
        String graphics = graphicsName();
        boolean vSync = Boolean.parseBoolean(System.getProperty("libfdx.benchmark.vsync", "false"));
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Benchmark: " + benchmarkName + " - " + graphicsDisplayName(graphics))
                .size(640, 480)
                .visible(Boolean.parseBoolean(System.getProperty("libfdx.benchmark.visible", "true")))
                .vSync(vSync)
                .foregroundFps(Integer.parseInt(System.getProperty("libfdx.benchmark.foregroundFps", "0")))
                .graphics(graphicsProvider(graphics, vSync));

        new DesktopApplicationBackend().start(config, benchmark(benchmarkName));
    }

    private static ApplicationListener benchmark(String benchmarkName) {
        String normalized = benchmarkName != null ? benchmarkName.trim() : "";
        if (normalized.length() == 0 || SpriteBatchStressBenchmark.NAME.equals(normalized)) {
            return new SpriteBatchStressBenchmark(exitAfterNanos(), System.getProperty("libfdx.benchmark.result"));
        }
        throw new FdxException("Unknown benchmark: " + benchmarkName);
    }

    private static GraphicsAttachmentProvider graphicsProvider(String graphics, boolean vSync) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            DesktopVulkanProvider provider = new DesktopVulkanProvider().vSync(vSync).framesInFlight(3);
            if (!vSync) {
                provider.configuration().preferMailboxPresentMode(false);
            }
            return provider;
        }
        return new WGPUProvider().vSync(vSync).processEventsEachFrame(false);
    }

    private static String graphicsName() {
        String graphics = System.getProperty("libfdx.benchmark.graphics", "wgpu");
        if ("wgpu-jni".equalsIgnoreCase(graphics) || "wgpu-ffm".equalsIgnoreCase(graphics)) {
            return "wgpu";
        }
        return graphics;
    }

    private static String graphicsDisplayName(String graphics) {
        String configured = System.getProperty("libfdx.benchmark.graphicsLabel");
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

    private static long exitAfterNanos() {
        String value = System.getProperty("libfdx.benchmark.seconds", "8");
        if (value == null || value.trim().length() == 0) {
            return 0L;
        }
        double seconds = Double.parseDouble(value.trim());
        if (seconds <= 0.0) {
            return 0L;
        }
        double nanos = seconds * 1000000000.0;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) nanos;
    }
}
