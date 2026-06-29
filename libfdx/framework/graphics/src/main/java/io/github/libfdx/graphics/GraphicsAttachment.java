package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;

/**
 * Defines the contract for graphics attachment implementations.
 *
 * @author xpenatan
 */
public interface GraphicsAttachment extends GraphicsContext, Disposable {
    /**
     * Handles a size change.
     *
     * @param framebufferWidth the framebuffer width
     * @param framebufferHeight the framebuffer height
     */
    void resize(int framebufferWidth, int framebufferHeight);

    /**
     * Runs the process events step.
     */
    void processEvents();

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    boolean beginFrame();

    /**
     * Ends frame.
     */
    void endFrame();
}
