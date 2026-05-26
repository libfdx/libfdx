package io.github.libfdx.validation.scenario;

public final class ScenarioValidationConfig {
    public static final String PROPERTY_SCENARIO = "libfdx.validation.scenario";
    public static final String PROPERTY_MODE = "libfdx.validation.mode";
    public static final String PROPERTY_TIMEOUT_MS = "libfdx.validation.timeoutMs";
    public static final String PROPERTY_EVENTS = "libfdx.validation.events";
    public static final String PROPERTY_CAPTURE = "libfdx.validation.capture";
    public static final String PROPERTY_STEP_DELAY_SECONDS = "libfdx.validation.stepDelaySeconds";

    private final String selection;
    private final ScenarioValidationMode mode;
    private final long timeoutMillis;
    private final boolean eventsEnabled;
    private final ScenarioCapturePolicy capturePolicy;
    private final float stepDelaySeconds;

    private ScenarioValidationConfig(String selection, ScenarioValidationMode mode, long timeoutMillis,
            boolean eventsEnabled, ScenarioCapturePolicy capturePolicy, float stepDelaySeconds) {
        this.selection = selection != null && selection.length() > 0 ? selection : "all";
        this.mode = mode != null ? mode : ScenarioValidationMode.MIXED;
        this.timeoutMillis = Math.max(0L, timeoutMillis);
        this.eventsEnabled = eventsEnabled;
        this.capturePolicy = capturePolicy != null ? capturePolicy : ScenarioCapturePolicy.SCENARIO_LISTED;
        this.stepDelaySeconds = Math.max(0.0f, stepDelaySeconds);
    }

    public static ScenarioValidationConfig defaults() {
        return new ScenarioValidationConfig("all", ScenarioValidationMode.MIXED, 1000L, true,
                ScenarioCapturePolicy.SCENARIO_LISTED, 1.0f);
    }

    public static ScenarioValidationConfig fromSystemProperties() {
        ScenarioValidationConfig defaults = defaults();
        return new ScenarioValidationConfig(
                System.getProperty(PROPERTY_SCENARIO, defaults.selection()),
                ScenarioValidationMode.parse(System.getProperty(PROPERTY_MODE, defaults.mode().name())),
                parseLong(System.getProperty(PROPERTY_TIMEOUT_MS), defaults.timeoutMillis()),
                Boolean.parseBoolean(System.getProperty(PROPERTY_EVENTS, String.valueOf(defaults.eventsEnabled()))),
                ScenarioCapturePolicy.parse(System.getProperty(PROPERTY_CAPTURE, defaults.capturePolicy().name())),
                parseFloat(System.getProperty(PROPERTY_STEP_DELAY_SECONDS), defaults.stepDelaySeconds()));
    }

    public ScenarioValidationConfig selection(String selection) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public ScenarioValidationConfig mode(ScenarioValidationMode mode) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public ScenarioValidationConfig timeoutMillis(long timeoutMillis) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public ScenarioValidationConfig eventsEnabled(boolean eventsEnabled) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public ScenarioValidationConfig capturePolicy(ScenarioCapturePolicy capturePolicy) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public ScenarioValidationConfig stepDelaySeconds(float stepDelaySeconds) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    public String selection() {
        return selection;
    }

    public ScenarioValidationMode mode() {
        return mode;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean eventsEnabled() {
        return eventsEnabled;
    }

    public ScenarioCapturePolicy capturePolicy() {
        return capturePolicy;
    }

    public float stepDelaySeconds() {
        return stepDelaySeconds;
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid scenario validation timeout: " + value, ex);
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid scenario validation step delay seconds: " + value, ex);
        }
    }
}
