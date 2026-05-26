package io.github.libfdx.tests;

import io.github.libfdx.core.Logger;

public final class TestFpsLogger {
    private static final String PROPERTY = "libfdx.test.fpsLogSeconds";
    private static final float DEFAULT_INTERVAL_SECONDS = 1.0f;

    private final Logger logger;
    private final String label;
    private final float intervalSeconds;
    private float elapsedSeconds;
    private long frames;

    private TestFpsLogger(Logger logger, String label, float intervalSeconds) {
        this.logger = logger;
        this.label = label != null && label.trim().length() > 0 ? label.trim() : "Test";
        this.intervalSeconds = intervalSeconds;
    }

    public static TestFpsLogger create(Logger logger, String label) {
        return new TestFpsLogger(logger, label, intervalSeconds());
    }

    public void frame(float deltaSeconds, long totalFrames) {
        if (intervalSeconds <= 0.0f || logger == null) {
            return;
        }
        elapsedSeconds += Math.max(0.0f, deltaSeconds);
        frames++;
        if (elapsedSeconds + 0.0001f < intervalSeconds) {
            return;
        }
        float fps = elapsedSeconds > 0.0f ? frames / elapsedSeconds : 0.0f;
        float frameMillis = fps > 0.0f ? 1000.0f / fps : 0.0f;
        logger.info(label + " FPS: fps=" + rounded(fps)
                + ", avgFrameMs=" + rounded(frameMillis)
                + ", frames=" + frames
                + ", seconds=" + rounded(elapsedSeconds)
                + ", totalFrames=" + totalFrames);
        elapsedSeconds = 0.0f;
        frames = 0L;
    }

    public void reset() {
        elapsedSeconds = 0.0f;
        frames = 0L;
    }

    private static float intervalSeconds() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.trim().length() == 0) {
            return DEFAULT_INTERVAL_SECONDS;
        }
        try {
            float parsed = Float.parseFloat(value.trim());
            return parsed > 0.0f ? parsed : 0.0f;
        } catch (NumberFormatException ignored) {
            return DEFAULT_INTERVAL_SECONDS;
        }
    }

    private static String rounded(float value) {
        return String.valueOf(Math.round(value * 100.0f) / 100.0f);
    }
}
