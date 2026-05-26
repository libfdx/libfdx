package io.github.libfdx.validation.scenario;

public final class ScenarioFailure extends RuntimeException {
    public ScenarioFailure(String message) {
        super(message);
    }

    public ScenarioFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
