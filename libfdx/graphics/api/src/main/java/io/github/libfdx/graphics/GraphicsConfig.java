package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;

/**
 * Stores configuration values for a graphics.
 *
 * @author xpenatan
 */
public final class GraphicsConfig {
    private final GraphicsAttachmentProvider provider;
    private final Display display;

    private GraphicsConfig(GraphicsAttachmentProvider provider, Display display) {
        if (provider == null) {
            throw new FdxException("Graphics provider cannot be null");
        }
        this.provider = provider;
        this.display = display;
    }

    /**
     * Creates a graphics config.
     *
     * @param provider the provider
     * @return a new graphics config
     */
    public static GraphicsConfig provider(GraphicsAttachmentProvider provider) {
        return new GraphicsConfig(provider, null);
    }

    /**
     * Returns the provider.
     *
     * @return the provider
     */
    public GraphicsAttachmentProvider provider() {
        return provider;
    }

    /**
     * Sets the display and returns this graphics config.
     *
     * @param display the display
     * @return this graphics config for chaining
     */
    public GraphicsConfig display(Display display) {
        if (display == null) {
            throw new FdxException("Graphics display cannot be null");
        }
        return new GraphicsConfig(provider, display);
    }

    /**
     * Returns the display.
     *
     * @return the display
     */
    public Display display() {
        return display;
    }
}
