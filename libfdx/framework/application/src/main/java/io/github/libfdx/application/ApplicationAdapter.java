package io.github.libfdx.application;

import io.github.libfdx.Fdx;

/**
 * Provides default behavior for application callbacks.
 *
 * @author xpenatan
 */
public class ApplicationAdapter implements ApplicationListener {
    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
    }

    /**
     * Handles application pause.
     */
    @Override
    public void pause() {
    }

    /**
     * Handles application resume.
     */
    @Override
    public void resume() {
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
    }
}
