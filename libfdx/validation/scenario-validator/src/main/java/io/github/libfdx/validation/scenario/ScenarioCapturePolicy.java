package io.github.libfdx.validation.scenario;

public enum ScenarioCapturePolicy {
    ALL,
    FAILED,
    NONE,
    SCENARIO_LISTED;

    public static ScenarioCapturePolicy parse(String value) {
        if (value == null || value.length() == 0) {
            return SCENARIO_LISTED;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        for (ScenarioCapturePolicy policy : values()) {
            if (policy.name().equals(normalized)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("Unknown scenario capture policy: " + value);
    }
}
