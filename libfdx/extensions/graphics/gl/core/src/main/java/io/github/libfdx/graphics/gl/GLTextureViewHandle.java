package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

final class GLTextureViewHandle implements TextureView {
    private final ProviderId providerId;
    private final TextureFormat format;

    GLTextureViewHandle(ProviderId providerId, TextureFormat format) {
        this.providerId = providerId;
        this.format = format;
    }

    @Override
    public TextureFormat format() {
        return format;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
