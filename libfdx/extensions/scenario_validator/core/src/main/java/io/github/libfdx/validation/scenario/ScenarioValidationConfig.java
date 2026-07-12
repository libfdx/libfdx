package io.github.libfdx.validation.scenario;

/**
 * Stores configuration values for a scenario validation.
 *
 * @author xpenatan
 */
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
        String normalizedSelection = selection != null ? selection.trim() : "";
        this.selection = normalizedSelection.length() > 0 ? normalizedSelection : "all";
        this.mode = mode != null ? mode : ScenarioValidationMode.MIXED;
        this.timeoutMillis = Math.max(0L, timeoutMillis);
        this.eventsEnabled = eventsEnabled;
        this.capturePolicy = capturePolicy != null ? capturePolicy : ScenarioCapturePolicy.SCENARIO_LISTED;
        this.stepDelaySeconds = Math.max(0.0f, stepDelaySeconds);
    }

    /**
     * Creates a scenario validation config.
     *
     * @return a new scenario validation config
     */
    public static ScenarioValidationConfig defaults() {
        return new ScenarioValidationConfig("all", ScenarioValidationMode.MIXED, 1000L, true,
                ScenarioCapturePolicy.SCENARIO_LISTED, 1.0f);
    }

    /**
     * Creates a scenario validation config.
     *
     * @return a new scenario validation config
     */
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

    /**
     * Sets the selection and returns this scenario validation config.
     *
     * @param selection the selection
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig selection(String selection) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Sets the mode and returns this scenario validation config.
     *
     * @param mode the mode
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig mode(ScenarioValidationMode mode) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Sets the timeout millis and returns this scenario validation config.
     *
     * @param timeoutMillis the timeout millis
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig timeoutMillis(long timeoutMillis) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Sets the events enabled and returns this scenario validation config.
     *
     * @param eventsEnabled the events enabled
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig eventsEnabled(boolean eventsEnabled) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Sets the capture policy and returns this scenario validation config.
     *
     * @param capturePolicy the capture policy
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig capturePolicy(ScenarioCapturePolicy capturePolicy) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Sets the step delay seconds and returns this scenario validation config.
     *
     * @param stepDelaySeconds the step delay seconds
     * @return this scenario validation config for chaining
     */
    public ScenarioValidationConfig stepDelaySeconds(float stepDelaySeconds) {
        return new ScenarioValidationConfig(selection, mode, timeoutMillis, eventsEnabled, capturePolicy,
                stepDelaySeconds);
    }

    /**
     * Returns the selection.
     *
     * @return the selection
     */
    public String selection() {
        return selection;
    }

    /**
     * Returns the mode.
     *
     * @return the mode
     */
    public ScenarioValidationMode mode() {
        return mode;
    }

    /**
     * Returns the timeout millis.
     *
     * @return the timeout millis
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Returns the events enabled.
     *
     * @return true if events enabled succeeds or is active; false otherwise
     */
    public boolean eventsEnabled() {
        return eventsEnabled;
    }

    /**
     * Returns the capture policy.
     *
     * @return the capture policy
     */
    public ScenarioCapturePolicy capturePolicy() {
        return capturePolicy;
    }

    /**
     * Returns the step delay seconds.
     *
     * @return the step delay seconds
     */
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
