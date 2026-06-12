package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.gl.GLSurface;
import org.lwjgl.glfw.GLFW;

/**
 * Represents a desktop GL surface.
 *
 * @author xpenatan
 */
final class DesktopGLSurface implements GLSurface {
    private final long windowHandle;

    DesktopGLSurface(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        GLFW.glfwMakeContextCurrent(windowHandle);
    }

    /**
     * Runs the swap buffers step.
     */
    @Override
    public void swapBuffers() {
        GLFW.glfwSwapBuffers(windowHandle);
    }

    /**
     * Runs the release current step.
     */
    @Override
    public void releaseCurrent() {
        GLFW.glfwMakeContextCurrent(0L);
    }
}
