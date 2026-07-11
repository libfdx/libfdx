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
    GraphicsAttachment create(GraphicsConfig config);

    /**
     * Destroys a graphics attachment created by this service.
     *
     * @param context the graphics context
     */
    default void destroy(GraphicsContext context) {
        if (context == null || context == main()) {
            return;
        }
        if (context instanceof GraphicsAttachment) {
            GraphicsAttachment attachment = (GraphicsAttachment) context;
            if (!attachment.isDisposed()) {
                attachment.dispose();
            }
        }
    }
}
