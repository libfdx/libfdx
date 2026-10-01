package io.github.libfdx.testsupport;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;

/** Optional, bounded measurement of an unchanged shared test scene. */
public final class PerformanceApplication implements ApplicationListener {
    private final ApplicationListener scene;
    private final String testName;
    private final FrameTiming timing;
    private final long duration;
    private final long measuredFrames;
    private final long warmupFrames;
    private final double minimumFps;
    private Fdx fdx;
    private boolean complete;
    private boolean ready;
    private long frameStart;
    private String workload;
    private long preparationStart;
    private long readyNanos;

    private PerformanceApplication(ApplicationListener scene, String testName) {
        this.scene = scene;
        this.testName = testName;
        measuredFrames = frames("frames", 0);
        warmupFrames = frames("warmupFrames", 60);
        timing =
                measuredFrames > 0
                        ? new FrameTiming(0, warmupFrames)
                        : new FrameTiming((long) (seconds("warmupSeconds", 3, true) * 1e9));
        duration = (long) (seconds("seconds", 8, false) * 1e9);
        minimumFps = seconds("minFps", 0, true);
    }

    public static boolean enabled() {
        return Boolean.getBoolean("libfdx.test.performance");
    }

    public static ApplicationListener wrap(ApplicationListener scene, String testName) {
        if (!enabled()) return scene;
        if (testName.equals(TestSelector.AUTO_TEST_NAME)) {
            throw new IllegalArgumentException(
                    "Performance measurements require a single scene, not the automatic suite");
        }
        return new PerformanceApplication(scene, testName);
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        preparationStart = System.nanoTime();
        for (TestSelector.TestDescriptor descriptor : TestSelector.descriptors()) {
            if (!descriptor.name().equals(testName)) continue;
            if (!descriptor.supports(fdx.graphics().main().device().capabilities())
                    || (descriptor.requiresAudio() && fdx.audio() == null)) {
                System.out.println(
                        "[performance-unavailable] test="
                                + testName
                                + " provider="
                                + fdx.graphics().main().providerId()
                                + " reason=required_capability_or_audio");
                throw new UnsupportedOperationException(
                        "Performance scene requirements are unavailable: " + testName);
            }
            break;
        }
        scene.create(fdx);
    }

    @Override
    public void resize(int width, int height) {
        scene.resize(width, height);
    }

    @Override
    public void pause() {
        scene.pause();
    }

    @Override
    public void resume() {
        scene.resume();
    }

    @Override
    public void render() {
        frameStart = System.nanoTime();
        scene.render();
    }

    @Override
    public void onFrameEnd() {
        scene.onFrameEnd();
        long end = System.nanoTime();
        if (!ready) {
            ready =
                    !(scene instanceof TestReadiness readiness)
                            || readiness.readyForAutomaticCompletion();
            if (ready) {
                readyNanos = end - preparationStart;
                workload = workloadSignature();
            }
        }
        if (ready) timing.record(frameStart, end);
        if (measuredFrames > 0
                ? timing.samples() >= measuredFrames
                : timing.measuredNanos() >= duration) {
            complete = true;
            fdx.app().requestExit();
        }
    }

    @Override
    public void dispose() {
        scene.dispose();
        if (!complete) throw new FdxException("Performance run ended before measurement completed");
        var display = fdx.displays().main();
        System.out.println("[performance-workload] " + workload);
        System.out.println("[performance-readiness] test=" + testName + " preparationMs=" + readyNanos / 1e6);
        System.out.println(
                "[performance] test="
                        + testName
                        + " provider="
                        + fdx.graphics().main().providerId()
                        + " width="
                        + display.framebufferWidth()
                        + " height="
                        + display.framebufferHeight()
                        + " frames="
                        + timing.samples()
                        + " seconds="
                        + timing.measuredNanos() / 1e9
                        + " fps="
                        + timing.fps()
                        + " meanMs="
                        + timing.meanMillis()
                        + " p95Ms="
                        + timing.percentileMillis(0.95)
                        + " p99Ms="
                        + timing.percentileMillis(0.99)
                        + " renderMeanMs="
                        + timing.renderMillis()
                        + " measurement="
                        + (measuredFrames > 0 ? "frames" : "time")
                        + " requestedFrames="
                        + measuredFrames
                        + " warmupFrames="
                        + (measuredFrames > 0 ? warmupFrames : 0));
        if (timing.fps() < minimumFps) {
            throw new FdxException("FPS verification failed: " + timing.fps() + " < " + minimumFps);
        }
    }

    private String workloadSignature() {
        var capabilities = fdx.graphics().main().device().capabilities();
        StringBuilder value = new StringBuilder("schema=1;test=").append(testName)
                .append(";scene=").append(scene instanceof PerformanceWorkload described
                        ? described.performanceWorkload() : "registry-default")
                .append(";sprites=").append(System.getProperty("libfdx.test.spriteCount", "20000"))
                .append(";particleDelta=").append(System.getProperty("libfdx.test.particleDelta", "0"))
                .append(";features=");
        for (var feature : io.github.libfdx.graphics.GraphicsFeature.values())
            value.append(capabilities.supports(feature) ? '1' : '0');
        value.append(";formats=");
        for (var format : io.github.libfdx.graphics.TextureFormat.values()) {
            value.append(capabilities.supportsColorFormat(format) ? '1' : '0')
                    .append(capabilities.supportsDepthStencilFormat(format) ? '1' : '0')
                    .append(capabilities.supportsColorFiltering(format) ? '1' : '0')
                    .append(capabilities.supportsColorBlending(format) ? '1' : '0')
                    .append(capabilities.supportsResolveFormat(format) ? '1' : '0');
            for (int samples : new int[] {1, 2, 4, 8, 16})
                value.append(capabilities.supportsSampleCount(format, samples) ? '1' : '0');
        }
        return value.toString();
    }

    private static double seconds(String name, double fallback, boolean zeroAllowed) {
        double value =
                Double.parseDouble(
                        System.getProperty(
                                "libfdx.test.performance." + name, Double.toString(fallback)));
        if (!Double.isFinite(value) || value < 0 || (!zeroAllowed && value == 0)) {
            throw new IllegalArgumentException("Invalid performance setting: " + name);
        }
        return value;
    }

    private static long frames(String name, long fallback) {
        long value =
                Long.parseLong(
                        System.getProperty(
                                "libfdx.test.performance." + name, Long.toString(fallback)));
        if (value < 0) throw new IllegalArgumentException("Invalid performance setting: " + name);
        return value;
    }
}
