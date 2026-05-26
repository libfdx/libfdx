package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.gl.GLSurface;
import org.lwjgl.glfw.GLFW;

final class DesktopGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    @Override
    public void makeCurrent() {
        GLFW.glfwMakeContextCurrent(windowHandle);
    }

    @Override
    public void swapBuffers() {
        GLFW.glfwSwapBuffers(windowHandle);
    }

    @Override
    public void releaseCurrent() {
        GLFW.glfwMakeContextCurrent(0L);
    }
}
