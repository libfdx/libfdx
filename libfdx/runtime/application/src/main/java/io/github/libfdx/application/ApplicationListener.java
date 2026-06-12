package io.github.libfdx.application;

import io.github.libfdx.Fdx;

/**
 * Receives callbacks for application events.
 *
 * @author xpenatan
 */
public interface ApplicationListener {
    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    void create(Fdx fdx);

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    void resize(int width, int height);

    /**
     * Renders the current content.
     */
    void render();

    /**
     * Optional callback invoked while the backend still has an active frame,
     * after {@link #render()} has completed for the frame.
     * Implementations may use this hook for frame-end work such as input
     * synthesis checks, screenshot capture, and visual validation.
     */
    default void onFrameEnd() {
    }

    /**
     * Handles application pause.
     */
    void pause();

    /**
     * Handles application resume.
     */
    void resume();

    /**
     * Releases resources held by this instance.
     */
    void dispose();
}
