package io.github.libfdx.application;

import io.github.libfdx.Fdx;

public interface ApplicationListener {
    void create(Fdx fdx);

    void resize(int width, int height);

    void render();

    /**
     * Optional callback invoked while the backend still has an active frame,
     * after {@link #render()} has completed for the frame.
     * Implementations may use this hook for frame-end work such as input
     * synthesis checks, screenshot capture, and visual validation.
     */
    default void onFrameEnd() {
    }

    void pause();

    void resume();

    void dispose();
}
