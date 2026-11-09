package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.tests.utils.uikittest.validation.UiKitFrameCapture.CapturedFrame;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Queues frame captures, writes PNGs, and compares visual baselines. */
public final class UiKitTestCapture {
    private static final String REQUESTED_SCENARIO = "requested";
    private static final int VISUAL_CHANNEL_TOLERANCE = 8;
    private static final float VISUAL_MISMATCH_RATIO = 0.01f;
    private static final String DEFAULT_CAPTURE_TEMPLATE = "build/reports/uikit/{scenario}-{frame}.png";
    private static final String DEFAULT_VISUAL_BASELINE_DIR = "tests/assets/ui-baselines";
    private static final String DEFAULT_VISUAL_BASELINE_TEMPLATE = "{scenario}.png";

    private final GraphicsContext graphics;
    private final Logger logger;
    private final boolean available;
    private final long captureFrame;
    private final String captureTemplate;
    private final String baselineDirectory;
    private final String baselineTemplate;
    private final boolean visualCaptureAllScenarios;
    private final boolean captureOutputRequested;
    private final int captureEveryFrames;
    private final int visualChannelTolerance;
    private final float visualMismatchRatio;
    private final boolean visualValidate;
    private final boolean visualValidateRequireBaselines;
    private final LinkedHashMap<String, Integer> pendingValidationCaptures = new LinkedHashMap<>();
    private boolean captured;

    public UiKitTestCapture(GraphicsContext graphics, Logger logger, boolean allowVisualValidation) {
        this.graphics = graphics;
        this.logger = logger;
        available = Boolean.parseBoolean(System.getProperty("libfdx.test.desktopImageCapture", "true"));
        String configuredTemplate = trimOrEmpty(System.getProperty("libfdx.test.capture"));
        captureOutputRequested = available && configuredTemplate != null;
        captureTemplate = configuredTemplate != null ? configuredTemplate : DEFAULT_CAPTURE_TEMPLATE;
        String configuredBaselineDirectory = trimOrEmpty(System.getProperty("libfdx.test.visualBaselineDir"));
        baselineDirectory = configuredBaselineDirectory != null ? configuredBaselineDirectory : DEFAULT_VISUAL_BASELINE_DIR;
        String configuredBaselineTemplate = trimOrEmpty(System.getProperty("libfdx.test.visualBaselineTemplate"));
        baselineTemplate = configuredBaselineTemplate != null ? configuredBaselineTemplate : DEFAULT_VISUAL_BASELINE_TEMPLATE;
        visualValidate = available && allowVisualValidation
                && Boolean.parseBoolean(System.getProperty("libfdx.test.visualValidate", "false"));
        visualValidateRequireBaselines = Boolean.parseBoolean(System.getProperty("libfdx.test.visualRequireBaselines", "false"));
        visualCaptureAllScenarios = available
                && Boolean.parseBoolean(System.getProperty("libfdx.test.visualCaptureAllScenarios", "false"));
        int configuredEveryFrames = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));
        captureEveryFrames = available ? Math.max(0, configuredEveryFrames) : 0;
        visualChannelTolerance = Integer.parseInt(System.getProperty("libfdx.test.visualChannelTolerance",
                String.valueOf(VISUAL_CHANNEL_TOLERANCE)));
        visualMismatchRatio = Float.parseFloat(System.getProperty("libfdx.test.visualMismatchRatio",
                String.valueOf(VISUAL_MISMATCH_RATIO)));
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "0"));
    }

    public boolean available() {
        return available;
    }

    boolean captureAllScenarios() {
        return visualCaptureAllScenarios;
    }

    public boolean visualValidationEnabled() {
        return visualValidate;
    }

    public String template() {
        return captureTemplate;
    }

    String outputPath(String scenario, long frame) {
        return available ? resolveCapturePath(captureTemplate, scenario, frame) : null;
    }

    public void afterRender(long renderedFrames) {
        flushPendingValidationCaptures(renderedFrames);
        captureRequestedFrame(renderedFrames);
        if (shouldCaptureEveryFrame(renderedFrames)) {
            queue("capture-every-" + renderedFrames, true, false);
        }
    }

    void queue(String scenario, boolean captureImage, boolean validateVisual) {
        if (!available) {
            return;
        }
        String safeScenario = safeScenarioName(scenario);
        Integer combined = pendingValidationCaptures.get(safeScenario);
        int flags = combined == null ? 0 : combined;
        if (captureImage) {
            flags |= 1;
        }
        if (validateVisual) {
            flags |= 2;
        }
        if (flags != 0) {
            pendingValidationCaptures.put(safeScenario, flags);
        }
    }

    private void flushPendingValidationCaptures(long renderedFrames) {
        if (pendingValidationCaptures.isEmpty()) {
            return;
        }
        CapturedFrame capturedFrame = null;
        for (java.util.Map.Entry<String, Integer> pending : pendingValidationCaptures.entrySet()) {
            int flags = pending.getValue();
            boolean requestCapture = (flags & 1) != 0;
            boolean requestValidate = shouldValidateVisual(pending.getKey(), flags);
            if (requestCapture || requestValidate) {
                if (capturedFrame == null) {
                    capturedFrame = UiKitFrameCapture.capture(graphics.currentFrame());
                }
                captureValidationFrame(pending.getKey(), requestCapture, requestValidate, capturedFrame, renderedFrames);
            }
        }
        pendingValidationCaptures.clear();
    }

    private boolean shouldValidateVisual(String safeScenario, int flags) {
        if ((flags & 2) != 0) {
            return true;
        }
        return visualValidate && !REQUESTED_SCENARIO.equals(safeScenario);
    }

    private void captureRequestedFrame(long renderedFrames) {
        if (captureOutputRequested && !captured && renderedFrames >= captureFrame) {
            queue(REQUESTED_SCENARIO, true, false);
        }
    }

    private void captureValidationFrame(String scenario, boolean requestCapture, boolean requestValidate,
            CapturedFrame image, long renderedFrames) {
        if (!requestCapture && !requestValidate) {
            return;
        }
        if (requestCapture) {
            writeImage(image, resolveCapturePath(captureTemplate, scenario, renderedFrames), "capture");
            captured = captured || shouldPersistCaptureOutput(renderedFrames);
        }
        if (requestValidate) {
            validateVisualMatch(scenario, renderedFrames, image);
        }
    }

    private boolean shouldCaptureEveryFrame(long renderedFrames) {
        return captureEveryFrames > 0 && renderedFrames > 0L && renderedFrames % captureEveryFrames == 0L;
    }

    private void validateVisualMatch(String scenario, long frame, CapturedFrame current) {
        if (!visualValidate) {
            return;
        }
        String safeScenario = safeScenarioName(scenario);
        String baselinePath = resolveCapturePath(baselineTemplate, safeScenario, frame);
        File baseline = new File(baselineDirectory, baselinePath);
        if (!baseline.isFile()) {
            if (visualValidateRequireBaselines) {
                throw new FdxException("UiKitTest visual baseline not found for scenario " + safeScenario + ": "
                        + baseline.getAbsolutePath());
            }
            if (logger != null) {
                logger.info("UiKitTest visual baseline not found for scenario " + safeScenario + ", skipping compare: "
                        + baseline.getAbsolutePath());
            }
            return;
        }

        CapturedFrame expected = UiKitFrameCapture.readImage(baseline, safeScenario);
        if (expected.width() != current.width() || expected.height() != current.height()) {
            throw new FdxException("UiKitTest visual dimension mismatch for " + safeScenario
                    + ": expected " + expected.width() + "x" + expected.height()
                    + ", actual " + current.width() + "x" + current.height());
        }

        int width = current.width();
        int height = current.height();
        int mismatchPixels = 0;
        int maxChannelDiff = 0;
        int total = width * height;
        for (int i = 0; i < total; i++) {
            int actual = current.argb()[i];
            int expectedPixel = expected.argb()[i];
            int diffR = Math.abs(((actual >>> 16) & 0xff) - ((expectedPixel >>> 16) & 0xff));
            int diffG = Math.abs(((actual >>> 8) & 0xff) - ((expectedPixel >>> 8) & 0xff));
            int diffB = Math.abs((actual & 0xff) - (expectedPixel & 0xff));
            int pixelMax = Math.max(Math.max(diffR, diffG), diffB);
            if (pixelMax > visualChannelTolerance) {
                mismatchPixels++;
            }
            if (pixelMax > maxChannelDiff) {
                maxChannelDiff = pixelMax;
            }
        }

        float mismatchRatio = total == 0 ? 1.0f : (float) mismatchPixels / (float) total;
        if (mismatchRatio > visualMismatchRatio) {
            if (captureOutputRequested || captureEveryFrames > 0) {
                writeImage(current, resolveCapturePath("build/reports/uikit/{scenario}-{frame}-mismatch.png", scenario, frame),
                        "visual mismatch");
            }
            throw new FdxException("UiKitTest visual check failed for " + safeScenario
                    + ": mismatch ratio=" + mismatchRatio + ", max channel diff=" + maxChannelDiff
                    + ", total=" + total + ", mismatched=" + mismatchPixels);
        }
    }

    private boolean shouldPersistCaptureOutput(long renderedFrames) {
        return captureOutputRequested && (captureFrame <= renderedFrames || captureEveryFrames > 0);
    }

    private void writeImage(CapturedFrame image, String path, String actionLabel) {
        try {
            File output = new File(path);
            File parent = output.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            UiKitFrameCapture.writeDesktopPng(image, output);
            if (logger != null) {
                logger.info("UiKitTest " + actionLabel + " " + output.getPath());
            }
        } catch (Exception e) {
            throw new FdxException("Could not write image for " + actionLabel, e);
        }
    }

    private String resolveCapturePath(String template, String scenario, long frame) {
        String targetScenario = safeScenarioName(scenario);
        String path = template;
        path = path.replace("{scenario}", targetScenario);
        path = path.replace("{frame}", String.valueOf(frame));
        path = path.replace("{name}", targetScenario);
        return path;
    }

    private String safeScenarioName(String label) {
        if (label == null || label.length() == 0) {
            return "uikit";
        }
        String normalized = label.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-\\.]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.length() == 0 ? "uikit" : normalized;
    }

    private String trimOrEmpty(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    void verifyRequestedCapture(long renderedFrames) {
        if (captureOutputRequested && captureFrame <= renderedFrames && !captured) {
            throw new FdxException("UiKitTest did not capture a validation frame");
        }
    }

}
