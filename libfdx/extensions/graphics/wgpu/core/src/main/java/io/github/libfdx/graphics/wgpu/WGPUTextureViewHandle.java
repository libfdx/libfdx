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
    private final WGPUResourceDomain resourceDomain;
    private final WGPUTextureHandle textureHandle;
    private final WGPUContext frameOwner;
    private final WGPUTextureView frameView;
    private final TextureFormat format;

    WGPUTextureViewHandle(WGPUContext frameOwner, WGPUTextureView frameView, TextureFormat format) {
        this.resourceDomain = frameOwner.resourceDomain();
        this.textureHandle = null;
        this.frameOwner = frameOwner;
        this.frameView = frameView;
        this.format = format;
    }

    WGPUTextureViewHandle(WGPUTextureHandle textureHandle) {
        this.resourceDomain = textureHandle.resourceDomain();
        this.textureHandle = textureHandle;
        this.frameOwner = null;
        this.frameView = null;
        this.format = textureHandle.format();
    }

    WGPUTextureView nativeView() {
        return textureHandle != null ? textureHandle.nativeView() : frameView;
    }

    int width() {
        return textureHandle != null ? textureHandle.width() : 0;
    }

    int height() {
        return textureHandle != null ? textureHandle.height() : 0;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    WGPUTextureHandle textureHandle() {
        return textureHandle;
    }

    WGPUContext frameOwner() {
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
