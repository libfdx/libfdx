package io.github.libfdx.backend.desktop;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Stores configuration values for a desktop application.
 *
 * @author xpenatan
 */
public final class DesktopApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig();
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
     * Sets the display config and returns this desktop application config.
     *
     * @param displayConfig the display config
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig();
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
     * Sets the graphics and returns this desktop application config.
     *
     * @param graphics the graphics context
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    /**
     * Sets the title and returns this desktop application config.
     *
     * @param title the title
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the size and returns this desktop application config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    /**
     * Sets the resizable and returns this desktop application config.
     *
     * @param resizable the resizable
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig resizable(boolean resizable) {
        displayConfig.resizable(resizable);
        return this;
    }

    /**
     * Sets the visible and returns this desktop application config.
     *
     * @param visible the visible
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig visible(boolean visible) {
        displayConfig.visible(visible);
        return this;
    }

    /**
     * Sets the v sync and returns this desktop application config.
     *
     * @param vSync the v sync
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    /**
     * Sets the foreground fps and returns this desktop application config.
     *
     * @param foregroundFps the foreground fps
     * @return this desktop application config for chaining
     */
    public DesktopApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
