package io.github.libfdx.graphics.gl;

/**
 * Defines the contract for GL surface implementations.
 *
 * @author xpenatan
 */
public interface GLSurface {
    /**
     * Runs the make current step.
     */
    void makeCurrent();

    /**
     * Runs the swap buffers step.
     */
    void swapBuffers();

    /**
     * Runs the release current step.
     */
    void releaseCurrent();
}
