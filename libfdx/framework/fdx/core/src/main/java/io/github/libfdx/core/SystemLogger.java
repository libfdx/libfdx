package io.github.libfdx.core;

/**
 * Represents a system logger.
 *
 * @author xpenatan
 */
public final class SystemLogger implements Logger {
    /**
     * Runs the debug step.
     *
     * @param message the message
     */
    @Override
    public void debug(String message) {
        System.out.println("[debug] " + message);
    }

    /**
     * Runs the info step.
     *
     * @param message the message
     */
    @Override
    public void info(String message) {
        System.out.println("[info] " + message);
    }

    /**
     * Runs the warn step.
     *
     * @param message the message
     */
    @Override
    public void warn(String message) {
        System.out.println("[warn] " + message);
    }

    /**
     * Runs the error step.
     *
     * @param message the message
     */
    @Override
    public void error(String message) {
        System.err.println("[error] " + message);
    }

    /**
     * Runs the error step.
     *
     * @param message the message
     * @param error the error
     */
    @Override
    public void error(String message, Throwable error) {
        error(message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
