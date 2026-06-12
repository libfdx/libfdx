package io.github.libfdx.core;

/**
 * Defines the contract for logger implementations.
 *
 * @author xpenatan
 */
public interface Logger extends FdxService {
    /**
     * Runs the debug step.
     *
     * @param message the message
     */
    void debug(String message);

    /**
     * Runs the info step.
     *
     * @param message the message
     */
    void info(String message);

    /**
     * Runs the warn step.
     *
     * @param message the message
     */
    void warn(String message);

    /**
     * Runs the error step.
     *
     * @param message the message
     */
    void error(String message);

    /**
     * Runs the error step.
     *
     * @param message the message
     * @param error the error
     */
    void error(String message, Throwable error);
}
