package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;

/**
 * Represents a GL texture handle.
 *
 * @author xpenatan
 */
final class GLTextureHandle implements Texture {
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final int texture;
    private final int width;
    private final int height;
    private final TextureFormat format;
    private final TextureUsage usage;
    private final GLTextureViewHandle view;
    private boolean disposed;

    GLTextureHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int texture, int width, int height,
            TextureFormat format, TextureUsage usage) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        view = new GLTextureViewHandle(this);
    }

    int texture() {
        return texture;
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
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
     * Returns the format.
     *
     * @return the format
     */
    @Override
    public TextureFormat format() {
        return format;
    }

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    @Override
    public TextureUsage usage() {
        return usage;
    }

    /**
     * Returns the default texture view.
     *
     * @return the default texture view
     */
    @Override
    public TextureView view() {
        return view;
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

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (resourceDomain.makeAnyContextCurrent()) {
            gl.deleteTexture(texture);
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
