package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.TextureView;

/**
 * Immutable borrowed color/depth attachment set. Owns no textures; views become
 * invalid with their texture owners. Shader outputs and provider capabilities
 * must match the complete layout. Optional resolve views are borrowed as well.
 *
 * @author xpenatan
 */
public final class DefaultRenderTarget3D implements RenderTarget3D {
    private final int width;
    private final int height;
    private final TextureView[] colorAttachments;
    private final TextureView depthAttachment;
    private final TextureView[] resolveAttachments;

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
        this(width,height,colorAttachments,null,depthAttachment);
    }

    /** Borrows immutable attachment sets; null resolve array disables resolving, otherwise one nullable entry per color. */
    public DefaultRenderTarget3D(int width, int height, TextureView[] colorAttachments,
            TextureView[] resolveAttachments, TextureView depthAttachment) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("RenderTarget3D dimensions must be greater than zero");
        }
        if (colorAttachments == null || colorAttachments.length == 0 || colorAttachments[0] == null) {
            throw new FdxException("RenderTarget3D requires at least one color attachment");
        }
        int samples = colorAttachments[0].sampleCount();
        for (TextureView color : colorAttachments) {
            if (color == null || !color.format().isColor()) throw new FdxException("Color attachment requires a color view");
            validateView(color, width, height, samples);
        }
        if (depthAttachment != null) {
            if (!depthAttachment.format().isDepthStencil()) throw new FdxException("Depth attachment requires a depth view");
            validateView(depthAttachment, width, height, samples);
        }
        this.width = width;
        this.height = height;
        this.colorAttachments = colorAttachments.clone();
        if (resolveAttachments != null && resolveAttachments.length != colorAttachments.length) {
            throw new FdxException("Resolve attachment count must match colors");
        }
        this.resolveAttachments = resolveAttachments == null ? new TextureView[colorAttachments.length] : resolveAttachments.clone();
        for (int i=0;i<this.resolveAttachments.length;i++) {
            TextureView resolve=this.resolveAttachments[i];
            if (resolve == null) continue;
            if (samples<=1 || resolve.format()!=colorAttachments[i].format()) throw new FdxException("Incompatible resolve format or source sample count");
            validateView(resolve,width,height,1);
        }
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

    @Override public TextureView resolveAttachment(int index) { return resolveAttachments[index]; }

    private static void validateView(TextureView view, int width, int height, int samples) {
        if (view.sampleCount() != samples) throw new FdxException("Target attachment sample counts differ");
        if ((view.width() == 0) != (view.height() == 0) || view.width() < 0 || view.height() < 0
                || view.width() > 0 && (view.width() != width || view.height() != height)) {
            throw new FdxException("Target attachment dimensions differ");
        }
    }
}
