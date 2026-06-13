package io.github.libfdx.backend.desktopc;

import io.github.libfdx.graphics.gl.GLSurface;

/**
 * Represents a desktop C GL surface.
 *
 * @author xpenatan
 */
final class DesktopCGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopCGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        DesktopCGLFW.makeContextCurrent(windowHandle);
    }

    /**
     * Runs the swap buffers step.
     */
    @Override
    public void swapBuffers() {
        DesktopCGLFW.swapBuffers(windowHandle);
    }

    /**
     * Runs the release current step.
     */
    @Override
    public void releaseCurrent() {
        DesktopCGLFW.makeContextCurrent(0L);
    }
}
