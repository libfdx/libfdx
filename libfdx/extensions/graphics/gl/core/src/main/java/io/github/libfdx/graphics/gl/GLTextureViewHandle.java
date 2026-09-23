package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

/**
 * Represents a GL texture view handle.
 *
 * @author xpenatan
 */
final class GLTextureViewHandle implements TextureView {
    private final ProviderId providerId;
    private final GLResourceDomain resourceDomain;
    private final TextureFormat format;
    private final GLTextureHandle textureHandle;
    private final Object frameOwner;
    private final int level;

    GLTextureViewHandle(ProviderId providerId, GLResourceDomain resourceDomain, TextureFormat format,
            Object frameOwner) {
        this.providerId = providerId;
        this.resourceDomain = resourceDomain;
        this.format = format;
        this.textureHandle = null;
        this.frameOwner = frameOwner;
        level = 0;
    }

    GLTextureViewHandle(GLTextureHandle textureHandle) {
        this(textureHandle, 0);
    }

    GLTextureViewHandle(GLTextureHandle textureHandle, int level) {
        this.providerId = textureHandle.providerId();
        this.resourceDomain = textureHandle.resourceDomain();
        this.format = textureHandle.format();
        this.textureHandle = textureHandle;
        this.frameOwner = null;
        this.level = level;
    }

    boolean textureBacked() {
        return textureHandle != null;
    }

    @Override
    public int width() {
        return textureHandle != null ? textureHandle.mipWidth(level) : 0;
    }

    @Override
    public int height() {
        return textureHandle != null ? textureHandle.mipHeight(level) : 0;
    }

    @Override
    public int mipLevel() { return level; }
    @Override
    public int sampleCount() { return textureHandle != null ? textureHandle.sampleCount() : 1; }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
    }

    GLTextureHandle textureHandle() {
        return textureHandle;
    }

    Object frameOwner() {
        return frameOwner;
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
