package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;

final class GLTextureHandle implements Texture {
    private final ProviderId providerId;
    private final GLApi gl;
    private final int texture;
    private final int width;
    private final int height;
    private final TextureFormat format;
    private final TextureUsage usage;
    private boolean disposed;

    GLTextureHandle(ProviderId providerId, GLApi gl, int texture, int width, int height,
            TextureFormat format, TextureUsage usage) {
        this.providerId = providerId;
        this.gl = gl;
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
    }

    int texture() {
        return texture;
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
    public TextureFormat format() {
        return format;
    }

    @Override
    public TextureUsage usage() {
        return usage;
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        gl.deleteTexture(texture);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
