package io.github.libfdx.display;

/**
 * Stores configuration values for a display.
 *
 * @author xpenatan
 */
public final class DisplayConfig {
    private String title = "libfdx";
    private int width = 800;
    private int height = 600;
    private boolean resizable = true;
    private boolean visible = true;
    private boolean maximized;
    private boolean vSync = true;
    private int foregroundFps = 60;
    private int samples;

    /**
     * Returns the title.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * Sets the title and returns this display config.
     *
     * @param title the title
     * @return this display config for chaining
     */
    public DisplayConfig title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Sets the size and returns this display config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this display config for chaining
     */
    public DisplayConfig size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Returns the resizable.
     *
     * @return true if resizable succeeds or is active; false otherwise
     */
    public boolean resizable() {
        return resizable;
    }

    /**
     * Sets the resizable and returns this display config.
     *
     * @param resizable the resizable
     * @return this display config for chaining
     */
    public DisplayConfig resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    /**
     * Returns the visible.
     *
     * @return true if visible succeeds or is active; false otherwise
     */
    public boolean visible() {
        return visible;
    }

    /**
     * Sets the visible and returns this display config.
     *
     * @param visible the visible
     * @return this display config for chaining
     */
    public DisplayConfig visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Returns the maximized startup state.
     *
     * @return true if the display should start maximized; false otherwise
     */
    public boolean maximized() {
        return maximized;
    }

    /**
     * Sets the maximized startup state and returns this display config.
     *
     * @param maximized the maximized startup state
     * @return this display config for chaining
     */
    public DisplayConfig maximized(boolean maximized) {
        this.maximized = maximized;
        return this;
    }

    /**
     * Returns the v sync.
     *
     * @return true if v sync succeeds or is active; false otherwise
     */
    public boolean vSync() {
        return vSync;
    }

    /**
     * Sets the v sync and returns this display config.
     *
     * @param vSync the v sync
     * @return this display config for chaining
     */
    public DisplayConfig vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    /**
     * Returns the foreground fps.
     *
     * @return the foreground fps
     */
    public int foregroundFps() {
        return foregroundFps;
    }

    /**
     * Sets the foreground fps and returns this display config.
     *
     * @param foregroundFps the foreground fps
     * @return this display config for chaining
     */
    public DisplayConfig foregroundFps(int foregroundFps) {
        this.foregroundFps = foregroundFps;
        return this;
    }

    /**
     * Returns the requested framebuffer sample count.
     *
     * @return the sample count, or zero when multisampling is disabled
     */
    public int samples() {
        return samples;
    }

    /**
     * Sets the requested framebuffer sample count and returns this display config.
     *
     * @param samples the sample count, or zero to disable multisampling
     * @return this display config for chaining
     */
    public DisplayConfig samples(int samples) {
        this.samples = Math.max(0, samples);
        return this;
    }
}
