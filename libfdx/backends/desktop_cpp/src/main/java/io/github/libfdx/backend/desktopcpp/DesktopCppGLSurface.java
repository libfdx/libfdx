package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.graphics.gl.GLSurface;

/**
 * Represents a jNative GL surface.
 *
 * @author xpenatan
 */
final class DesktopCppGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopCppGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        DesktopCppGLFW.makeContextCurrent(windowHandle);
    }

    /**
     * Runs the swap buffers step.
     */
    @Override
    public void swapBuffers() {
        DesktopCppGLFW.swapBuffers(windowHandle);
    }

    /**
     * Runs the release current step.
     */
    @Override
    public void releaseCurrent() {
        DesktopCppGLFW.makeContextCurrent(0L);
    }
}
