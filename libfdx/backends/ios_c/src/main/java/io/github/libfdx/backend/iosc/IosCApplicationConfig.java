package io.github.libfdx.backend.iosc;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Stores configuration values for an iOS C application.
 *
 * @author xpenatan
 */
public final class IosCApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig().size(640, 480);
    private GraphicsAttachmentProvider graphics;

    /**
     * Returns the display config.
     *
     * @return the display config
     */
    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    /**
     * Sets the display config and returns this iOS C application config.
     *
     * @param displayConfig the display config
     * @return this iOS C application config for chaining
     */
    public IosCApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig().size(640, 480);
        return this;
    }

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    /**
     * Sets the graphics and returns this iOS C application config.
     *
     * @param graphics the graphics context
     * @return this iOS C application config for chaining
     */
    public IosCApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    /**
     * Sets the title and returns this iOS C application config.
     *
     * @param title the title
     * @return this iOS C application config for chaining
     */
    public IosCApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the size and returns this iOS C application config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this iOS C application config for chaining
     */
    public IosCApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    /**
     * Sets the v sync and returns this iOS C application config.
     *
     * @param vSync the v sync
     * @return this iOS C application config for chaining
     */
    public IosCApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }
}
