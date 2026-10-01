package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.audio.AudioProvider;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Stores configuration values for a jNative application.
 *
 * @author xpenatan
 */
public final class DesktopCppApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig();
    private GraphicsAttachmentProvider graphics;
    private AudioProvider audio;

    /** Optional backend-owned playback service; null disables audio. */
    public AudioProvider audio() {
        return audio;
    }

    /** Selects the audio provider to create and dispose with the application. */
    public DesktopCppApplicationConfig audio(AudioProvider audio) {
        this.audio = audio;
        return this;
    }

    /**
     * Returns the display config.
     *
     * @return the display config
     */
    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    /**
     * Sets the display config and returns this jNative application config.
     *
     * @param displayConfig the display config
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig displayConfig(DisplayConfig displayConfig) {
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
     * Sets the graphics and returns this jNative application config.
     *
     * @param graphics the graphics context
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    /**
     * Sets the title and returns this jNative application config.
     *
     * @param title the title
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the size and returns this jNative application config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    /**
     * Sets the resizable and returns this jNative application config.
     *
     * @param resizable the resizable
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig resizable(boolean resizable) {
        displayConfig.resizable(resizable);
        return this;
    }

    /**
     * Sets the visible and returns this jNative application config.
     *
     * @param visible the visible
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig visible(boolean visible) {
        displayConfig.visible(visible);
        return this;
    }

    /**
     * Sets the maximized startup state and returns this jNative application config.
     *
     * @param maximized the maximized startup state
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig maximized(boolean maximized) {
        displayConfig.maximized(maximized);
        return this;
    }

    /**
     * Sets the v sync and returns this jNative application config.
     *
     * @param vSync the v sync
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    /**
     * Sets the foreground fps and returns this jNative application config.
     *
     * @param foregroundFps the foreground fps
     * @return this jNative application config for chaining
     */
    public DesktopCppApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
