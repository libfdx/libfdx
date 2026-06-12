package io.github.libfdx.runtime.core;

import io.github.libfdx.core.FdxException;

/**
 * Signals runtime core failures.
 *
 * @author xpenatan
 */
public final class RuntimeCoreException extends FdxException {
    /**
     * Creates a runtime core exception.
     *
     * @param message the message
     */
    public RuntimeCoreException(String message) {
        super(message);
    }

    /**
     * Creates a runtime core exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public RuntimeCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}