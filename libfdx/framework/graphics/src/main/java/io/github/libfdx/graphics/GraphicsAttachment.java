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
     * This is surface resizing within the current resource domain, not recovery
     * of resources invalidated by context/device loss.
     *
     * @param framebufferWidth the framebuffer width
     * @param framebufferHeight the framebuffer height
     */
    void resize(int framebufferWidth, int framebufferHeight);

    /**
     * Runs the process events step.
     * Providers with loss detection may throw {@link GraphicsContextLostException};
     * callers must stop using that resource domain and perform normal cleanup.
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
