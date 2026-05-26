package io.github.libfdx.validation.scenario;

public enum ScenarioValidationMode {
    BEHAVIOR,
    VISUAL,
    MIXED;

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
