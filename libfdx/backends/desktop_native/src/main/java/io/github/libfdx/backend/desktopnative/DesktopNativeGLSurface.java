package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.graphics.gl.GLSurface;

final class DesktopNativeGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopNativeGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    @Override
    public void makeCurrent() {
        DesktopNativeGLFW.makeContextCurrent(windowHandle);
    }

    @Override
    public void swapBuffers() {
        DesktopNativeGLFW.swapBuffers(windowHandle);
    }

    @Override
    public void releaseCurrent() {
        DesktopNativeGLFW.makeContextCurrent(0L);
    }
}
