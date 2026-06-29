package io.github.libfdx.validation.scenario;

/**
 * Lists the supported scenario validation mode values.
 *
 * @author xpenatan
 */
public enum ScenarioValidationMode {
    BEHAVIOR,
    VISUAL,
    MIXED;

    /**
     * Creates a scenario validation mode.
     *
     * @param value the value
     * @return a new scenario validation mode
     */
    public static ScenarioValidationMode parse(String value) {
        if (value == null || value.length() == 0) {
            return MIXED;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ScenarioValidationMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown scenario validation mode: " + value);
    }
}
