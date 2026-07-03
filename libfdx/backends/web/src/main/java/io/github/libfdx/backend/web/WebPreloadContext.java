package io.github.libfdx.backend.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.ui.UiRoot;

/**
 * Provides state and rendering handles for a web preloading screen.
 *
 * @author xpenatan
 */
public final class WebPreloadContext {
    private final Fdx fdx;
    private final Display display;
    private final GraphicsContext graphics;
    private final UiRoot ui;
    private final WebPreloadProgress progress = new WebPreloadProgress();
    private float deltaTime;

    WebPreloadContext(Fdx fdx, Display display, GraphicsContext graphics, UiRoot ui) {
        this.fdx = fdx;
        this.display = display;
        this.graphics = graphics;
        this.ui = ui;
    }

    /**
     * Returns the typed Fdx root available during preloading.
     *
     * @return the typed Fdx root
     */
    public Fdx fdx() {
        return fdx;
    }

    /**
     * Returns the web display.
     *
     * @return the display
     */
    public Display display() {
        return display;
    }

    /**
     * Returns the main graphics context.
     *
     * @return the graphics context
     */
    public GraphicsContext graphics() {
        return graphics;
    }

    /**
     * Returns the UI kit root created for the preload screen.
     *
     * @return the preload UI root
     */
    public UiRoot ui() {
        return ui;
    }

    /**
     * Returns the latest asset preload progress.
     *
     * @return the preload progress
     */
    public WebPreloadProgress progress() {
        return progress;
    }

    /**
     * Returns the frame delta time in seconds.
     *
     * @return the delta time in seconds
     */
    public float deltaTime() {
        return deltaTime;
    }

    void update(float deltaTime) {
        this.deltaTime = deltaTime;
        progress.refresh();
    }

    void resize(int width, int height) {
        if (ui != null) {
            ui.resize(width, height);
        }
    }

    void dispose() {
        if (ui != null) {
            ui.dispose();
        }
    }
}
