package io.github.libfdx.core;

/**
 * Signals fdx failures.
 *
 * @author xpenatan
 */
public class FdxException extends RuntimeException {
    /**
     * Creates a fdx exception.
     *
     * @param message the message
     */
    public FdxException(String message) {
        super(message);
    }

    /**
     * Creates a fdx exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public FdxException(String message, Throwable cause) {
        super(message, cause);
    }
}
