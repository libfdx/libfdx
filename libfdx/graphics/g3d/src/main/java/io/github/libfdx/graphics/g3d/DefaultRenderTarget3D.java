package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.TextureView;

public final class DefaultRenderTarget3D implements RenderTarget3D {
    private final int width;
    private final int height;
    private final TextureView[] colorAttachments;
    private final TextureView depthAttachment;

    public DefaultRenderTarget3D(int width, int height, TextureView colorAttachment) {
        this(width, height, new TextureView[] { colorAttachment }, null);
    }

    public DefaultRenderTarget3D(int width, int height, TextureView[] colorAttachments, TextureView depthAttachment) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("RenderTarget3D dimensions must be greater than zero");
        }
        if (colorAttachments == null || colorAttachments.length == 0 || colorAttachments[0] == null) {
            throw new FdxException("RenderTarget3D requires at least one color attachment");
        }
        this.width = width;
        this.height = height;
        this.colorAttachments = colorAttachments.clone();
        this.depthAttachment = depthAttachment;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public TextureView colorAttachment(int index) {
        return colorAttachments[index];
    }

    @Override
    public TextureView depthAttachment() {
        return depthAttachment;
    }

    @Override
    public int colorAttachmentCount() {
        return colorAttachments.length;
    }
}
