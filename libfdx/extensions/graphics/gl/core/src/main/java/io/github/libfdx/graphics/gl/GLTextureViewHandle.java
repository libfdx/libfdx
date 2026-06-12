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
    private final TextureFormat format;

    GLTextureViewHandle(ProviderId providerId, TextureFormat format) {
        this.providerId = providerId;
        this.format = format;
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
