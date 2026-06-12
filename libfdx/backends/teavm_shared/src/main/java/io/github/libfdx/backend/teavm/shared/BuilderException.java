package io.github.libfdx.backend.teavm.shared;

/**
 * Signals builder failures.
 *
 * @author xpenatan
 */
public final class BuilderException extends RuntimeException {
    /**
     * Creates a builder exception.
     *
     * @param message the message
     */
    public BuilderException(String message) {
        super(message);
    }

    /**
     * Creates a builder exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public BuilderException(String message, Throwable cause) {
        super(message, cause);
    }
}
