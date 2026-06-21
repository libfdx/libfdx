package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUTextureView;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

/**
 * Represents a WGPU texture view handle.
 *
 * @author xpenatan
 */
final class WGPUTextureViewHandle implements TextureView {
    private final WGPUTextureView nativeView;
    private final TextureFormat format;
    private final int width;
    private final int height;

    WGPUTextureViewHandle(WGPUTextureView nativeView, TextureFormat format) {
        this(nativeView, format, 0, 0);
    }

    WGPUTextureViewHandle(WGPUTextureView nativeView, TextureFormat format, int width, int height) {
        this.nativeView = nativeView;
        this.format = format;
        this.width = width;
        this.height = height;
    }

    WGPUTextureView nativeView() {
        return nativeView;
    }

    int width() {
        return width;
    }

    int height() {
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
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
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
