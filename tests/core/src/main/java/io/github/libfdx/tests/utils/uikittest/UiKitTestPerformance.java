package io.github.libfdx.tests.utils.uikittest;

import io.github.libfdx.core.Logger;

/** Accumulates optional UI update and render timing. */
final class UiKitTestPerformance {
    private final Logger logger;
    private final float uiPerfLogSeconds;
    private float uiPerfElapsedSeconds;
    private long uiPerfFrames;
    private long uiPerfUpdateNanos;
    private long uiPerfRenderNanos;
    private long uiPerfFrameNanos;

    UiKitTestPerformance(Logger logger) {
        this.logger = logger;
        uiPerfLogSeconds = Float.parseFloat(System.getProperty("libfdx.test.uiPerfLogSeconds", "0"));
    }

    boolean enabled() {
        return uiPerfLogSeconds > 0.0f;
    }

    void recordFrame(float deltaSeconds, long updateNanos, long renderNanos, long frameNanos) {
        if (uiPerfLogSeconds <= 0.0f || logger == null) {
            return;
        }
        uiPerfElapsedSeconds += Math.max(0.0f, deltaSeconds);
        uiPerfFrames++;
        uiPerfUpdateNanos += Math.max(0L, updateNanos);
        uiPerfRenderNanos += Math.max(0L, renderNanos);
        uiPerfFrameNanos += Math.max(0L, frameNanos);
        if (uiPerfElapsedSeconds + 0.0001f < uiPerfLogSeconds) {
            return;
        }
        logger.info("UiKitTest perf: updateMs=" + roundedMillis(uiPerfUpdateNanos, uiPerfFrames)
                + ", renderMs=" + roundedMillis(uiPerfRenderNanos, uiPerfFrames)
                + ", frameWorkMs=" + roundedMillis(uiPerfFrameNanos, uiPerfFrames)
                + ", frames=" + uiPerfFrames
                + ", seconds=" + rounded(uiPerfElapsedSeconds));
        uiPerfElapsedSeconds = 0.0f;
        uiPerfFrames = 0L;
        uiPerfUpdateNanos = 0L;
        uiPerfRenderNanos = 0L;
        uiPerfFrameNanos = 0L;
    }

    private String roundedMillis(long nanos, long count) {
        if (count <= 0L) {
            return "0.0";
        }
        return rounded(nanos / 1000000.0f / count);
    }

    private String rounded(float value) {
        return String.valueOf(Math.round(value * 100.0f) / 100.0f);
    }

}
