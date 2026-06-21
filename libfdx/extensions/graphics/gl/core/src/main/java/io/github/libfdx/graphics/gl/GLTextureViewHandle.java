package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;

/**
 * Represents a GL texture view handle.
 *
 * @author xpenatan
 */
final class GLTextureViewHandle implements TextureView {
    private final ProviderId providerId;
    private final TextureFormat format;
    private final int texture;
    private final int width;
    private final int height;
    private final boolean renderAttachment;
    private int framebuffer;
    private int depthRenderbuffer;

    GLTextureViewHandle(ProviderId providerId, TextureFormat format) {
        this(providerId, format, 0, 0, 0, TextureUsage.SAMPLED);
    }

    GLTextureViewHandle(ProviderId providerId, TextureFormat format, int texture, int width, int height,
            TextureUsage usage) {
        this.providerId = providerId;
        this.format = format;
        this.texture = texture;
        this.width = width;
        this.height = height;
        renderAttachment = usage != null && usage.renderAttachment();
    }

    boolean textureBacked() {
        return texture != 0;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int framebuffer(GLApi gl) {
        if (!textureBacked() || !renderAttachment) {
            throw new FdxException("Texture view is not a GL render attachment");
        }
        if (framebuffer != 0) {
            return framebuffer;
        }
        framebuffer = gl.genFramebuffer();
        depthRenderbuffer = gl.genRenderbuffer();
        gl.bindFramebuffer(framebuffer);
        gl.framebufferTexture2D(texture);
        gl.bindRenderbuffer(depthRenderbuffer);
        gl.renderbufferStorageDepth(width, height);
        gl.framebufferRenderbufferDepth(depthRenderbuffer);
        gl.bindRenderbuffer(0);
        if (!gl.framebufferComplete()) {
            throw new FdxException("Could not create complete GL framebuffer for texture view");
        }
        gl.bindFramebuffer(0);
        return framebuffer;
    }

    void dispose(GLApi gl) {
        if (depthRenderbuffer != 0) {
            gl.deleteRenderbuffer(depthRenderbuffer);
            depthRenderbuffer = 0;
        }
        if (framebuffer != 0) {
            gl.deleteFramebuffer(framebuffer);
            framebuffer = 0;
        }
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    @Override
    public TextureFormat format() {
        return format;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
