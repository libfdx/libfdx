package io.github.libfdx.display;

import io.github.libfdx.core.FdxException;

/**
 * Provides the default implementation of a displays.
 *
 * @author xpenatan
 */
public final class DefaultDisplays implements Displays {
    private final Display main;

    /**
     * Creates a default displays.
     *
     * @param main the main
     */
    public DefaultDisplays(Display main) {
        if (main == null) {
            throw new FdxException("Main display cannot be null");
        }
        this.main = main;
    }

    /**
     * Runs the launcher entry point.
     *
     * @return the main
     */
    @Override
    public Display main() {
        return main;
    }

    /**
     * Returns the supports multiple.
     *
     * @return true if supports multiple succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsMultiple() {
        return false;
    }

    /**
     * Creates a value.
     *
     * @param config the configuration
     * @return the created value
     */
    @Override
    public Display create(DisplayConfig config) {
        throw new FdxException("This backend does not support creating additional displays");
    }
}
