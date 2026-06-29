package io.github.libfdx.graphics;

/**
 * Defines the contract for graphics implementations.
 *
 * @author xpenatan
 */
public interface Graphics {
    /**
     * Runs the launcher entry point.
     *
     * @return the main
     */
    GraphicsContext main();

    /**
     * Returns the supports multiple.
     *
     * @return true if supports multiple succeeds or is active; false otherwise
     */
    boolean supportsMultiple();

    /**
     * Creates a value.
     *
     * @param config the configuration
     * @return the created value
     */
    GraphicsContext create(GraphicsConfig config);
}
