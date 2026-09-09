package io.github.libfdx.graphics.gl;

import io.github.libfdx.graphics.GraphicsContextLostException;

/**
 * Defines the contract for GL surface implementations.
 *
 * @author xpenatan
 */
public interface GLSurface {
    /**
     * Makes the native context current. Report terminal native context loss with
     * {@link GraphicsContextLostException}; ordinary
     * surface errors must remain distinguishable from loss of the resource domain.
     */
    void makeCurrent();

    /**
     * Presents the surface. Report terminal native context loss with
     * {@link GraphicsContextLostException}.
     */
    void swapBuffers();

    /**
     * Runs the release current step.
     */
    void releaseCurrent();
}
