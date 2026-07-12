package io.github.libfdx.backend.desktop;

import io.github.libfdx.graphics.gl.GLSurface;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Represents a desktop GL surface.
 *
 * @author xpenatan
 */
final class DesktopGLSurface implements GLSurface {
    private final long windowHandle;
    private final GLCapabilities capabilities;

    DesktopGLSurface(long windowHandle, GLCapabilities capabilities) {
        this.windowHandle = windowHandle;
        this.capabilities = capabilities;
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.setCapabilities(capabilities);
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
        GL.setCapabilities(null);
    }
}
