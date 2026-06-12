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

    WGPUTextureViewHandle(WGPUTextureView nativeView, TextureFormat format) {
        this.nativeView = nativeView;
        this.format = format;
    }

    WGPUTextureView nativeView() {
        return nativeView;
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
