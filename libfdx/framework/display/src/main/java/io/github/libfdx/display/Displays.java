package io.github.libfdx.display;

/**
 * Defines the contract for displays implementations.
 *
 * @author xpenatan
 */
public interface Displays {
    /**
     * Runs the launcher entry point.
     *
     * @return the main
     */
    Display main();

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
    Display create(DisplayConfig config);

    /**
     * Destroys a display created by this service.
     *
     * @param display the display
     */
    default void destroy(Display display) {
        if (display == null || display == main()) {
            return;
        }
        display.requestClose();
    }
}
