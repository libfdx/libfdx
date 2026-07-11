package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Provides the default implementation of a graphics.
 *
 * @author xpenatan
 */
public final class DefaultGraphics implements Graphics {
    private final GraphicsContext main;

    /**
     * Creates a default graphics.
     *
     * @param main the main
     */
    public DefaultGraphics(GraphicsContext main) {
        if (main == null) {
            throw new FdxException("Main graphics context cannot be null");
        }
        this.main = main;
    }

    /**
     * Runs the launcher entry point.
     *
     * @return the main
     */
    @Override
    public GraphicsContext main() {
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
    public GraphicsAttachment create(GraphicsConfig config) {
        throw new FdxException("This backend does not support creating additional graphics contexts");
    }
}
