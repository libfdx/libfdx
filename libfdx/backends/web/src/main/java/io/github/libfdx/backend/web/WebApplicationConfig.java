package io.github.libfdx.backend.web;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Stores configuration values for a web application.
 *
 * @author xpenatan
 */
public final class WebApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig().size(640, 480);
    private GraphicsAttachmentProvider graphics;
    private WebPreloadApplicationListener preloadApplicationListener;
    private String canvasId = "libfdx-canvas";

    /**
     * Returns the display config.
     *
     * @return the display config
     */
    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    /**
     * Sets the display config and returns this web application config.
     *
     * @param displayConfig the display config
     * @return this web application config for chaining
     */
    public WebApplicationConfig displayConfig(DisplayConfig displayConfig) {
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
     * Sets the graphics and returns this web application config.
     *
     * @param graphics the graphics context
     * @return this web application config for chaining
     */
    public WebApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    /**
     * Returns the web preload application listener.
     *
     * @return the preload application listener, or null for the default listener
     */
    public WebPreloadApplicationListener preloadApplicationListener() {
        return preloadApplicationListener;
    }

    /**
     * Sets the web preload application listener and returns this web application config.
     *
     * @param preloadApplicationListener the preload application listener, or null for the default listener
     * @return this web application config for chaining
     */
    public WebApplicationConfig preloadApplicationListener(WebPreloadApplicationListener preloadApplicationListener) {
        this.preloadApplicationListener = preloadApplicationListener;
        return this;
    }

    /**
     * Returns whether this instance can vas ID.
     *
     * @return the canvas ID
     */
    public String canvasId() {
        return canvasId;
    }

    /**
     * Returns whether this instance can vas ID.
     *
     * @param canvasId the canvas ID
     * @return this web application config for chaining
     */
    public WebApplicationConfig canvasId(String canvasId) {
        this.canvasId = canvasId != null && canvasId.trim().length() > 0 ? canvasId : "libfdx-canvas";
        return this;
    }

    /**
     * Sets the title and returns this web application config.
     *
     * @param title the title
     * @return this web application config for chaining
     */
    public WebApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the size and returns this web application config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this web application config for chaining
     */
    public WebApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    /**
     * Sets the v sync and returns this web application config.
     *
     * @param vSync the v sync
     * @return this web application config for chaining
     */
    public WebApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    /**
     * Sets the foreground fps and returns this web application config.
     *
     * @param foregroundFps the foreground fps
     * @return this web application config for chaining
     */
    public WebApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
