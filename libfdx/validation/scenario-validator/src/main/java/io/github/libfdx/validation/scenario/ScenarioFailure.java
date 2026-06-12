package io.github.libfdx.validation.scenario;

/**
 * Represents a scenario failure.
 *
 * @author xpenatan
 */
public final class ScenarioFailure extends RuntimeException {
    /**
     * Creates a scenario failure.
     *
     * @param message the message
     */
    public ScenarioFailure(String message) {
        super(message);
    }

    /**
     * Creates a scenario failure.
     *
     * @param message the message
     * @param cause the cause
     */
    public ScenarioFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
