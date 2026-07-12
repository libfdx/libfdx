package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.TextureView;

/**
 * Provides the default implementation of a render target3 d.
 *
 * @author xpenatan
 */
public final class DefaultRenderTarget3D implements RenderTarget3D {
    private final int width;
    private final int height;
    private final TextureView[] colorAttachments;
    private final TextureView depthAttachment;

    /**
     * Creates a default render target3 d.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param colorAttachment the color attachment
     */
    public DefaultRenderTarget3D(int width, int height, TextureView colorAttachment) {
        this(width, height, new TextureView[] { colorAttachment }, null);
    }

    /**
     * Creates a default render target3 d.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param colorAttachments the color attachments
     * @param depthAttachment the depth attachment
     */
    public DefaultRenderTarget3D(int width, int height, TextureView[] colorAttachments, TextureView depthAttachment) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("RenderTarget3D dimensions must be greater than zero");
        }
        if (colorAttachments == null || colorAttachments.length == 0 || colorAttachments[0] == null) {
            throw new FdxException("RenderTarget3D requires at least one color attachment");
        }
        if (colorAttachments.length != 1) {
            throw new FdxException("DefaultRenderTarget3D currently supports exactly one color attachment");
        }
        if (depthAttachment != null) {
            throw new FdxException("DefaultRenderTarget3D does not yet support an explicit depth attachment");
        }
        this.width = width;
        this.height = height;
        this.colorAttachments = colorAttachments.clone();
        this.depthAttachment = depthAttachment;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    @Override
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    @Override
    public int height() {
        return height;
    }

    /**
     * Runs the color attachment step.
     *
     * @param index the index
     * @return the color attachment
     */
    @Override
    public TextureView colorAttachment(int index) {
        return colorAttachments[index];
    }

    /**
     * Returns the depth attachment.
     *
     * @return the depth attachment
     */
    @Override
    public TextureView depthAttachment() {
        return depthAttachment;
    }

    /**
     * Returns the color attachment count.
     *
     * @return the color attachment count
     */
    @Override
    public int colorAttachmentCount() {
        return colorAttachments.length;
    }
}
