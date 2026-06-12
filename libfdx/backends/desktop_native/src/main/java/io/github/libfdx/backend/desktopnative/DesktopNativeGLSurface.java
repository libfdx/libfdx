package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.graphics.gl.GLSurface;

/**
 * Represents a desktop native GL surface.
 *
 * @author xpenatan
 */
final class DesktopNativeGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopNativeGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        DesktopNativeGLFW.makeContextCurrent(windowHandle);
    }

    /**
     * Runs the swap buffers step.
     */
    @Override
    public void swapBuffers() {
        DesktopNativeGLFW.swapBuffers(windowHandle);
    }

    /**
     * Runs the release current step.
     */
    @Override
    public void releaseCurrent() {
        DesktopNativeGLFW.makeContextCurrent(0L);
    }
}
