package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.TextureView;

/**
 * Defines the contract for render target3 d implementations.
 *
 * @author xpenatan
 */
public interface RenderTarget3D {
    /**
     * Returns the width.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the height.
     *
     * @return the height
     */
    int height();

    /**
     * Runs the color attachment step.
     *
     * @param index the index
     * @return the color attachment
     */
    TextureView colorAttachment(int index);

    /** Borrowed single-sample resolve destination for a multisample color, or null when resolving is disabled. */
    default TextureView resolveAttachment(int index) { return null; }

    /**
     * Returns the depth attachment.
     *
     * @return the depth attachment
     */
    TextureView depthAttachment();

    /**
     * Returns the color attachment count.
     *
     * @return the color attachment count
     */
    int colorAttachmentCount();
}
