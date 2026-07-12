package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureView;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.TextureWrap;

/**
 * Represents a WGPU texture handle.
 *
 * @author xpenatan
 */
final class WGPUTextureHandle implements Texture {
    private final WGPUResourceDomain resourceDomain;
    private WGPUTextureAllocation allocation;
    private final WGPUTextureViewHandle view;
    private final String label;
    private final int width;
    private final int height;
    private final int mipLevelCount;
    private final TextureFormat format;
    private final TextureUsage usage;
    private final TextureFilter filter;
    private final TextureWrap wrapS;
    private final TextureWrap wrapT;
    private boolean disposed;

    WGPUTextureHandle(WGPUResourceDomain resourceDomain, WGPUTextureAllocation allocation, String label, int width,
            int height, int mipLevelCount, TextureFormat format, TextureUsage usage, TextureFilter filter,
            TextureWrap wrapS, TextureWrap wrapT) {
        if (resourceDomain == null || allocation == null || allocation.resourceDomain() != resourceDomain) {
            throw new FdxException("WGPU texture allocation is incompatible with its resource domain");
        }
        this.resourceDomain = resourceDomain;
        this.allocation = allocation;
        this.label = label != null ? label : "";
        this.width = width;
        this.height = height;
        this.mipLevelCount = Math.max(1, mipLevelCount);
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        this.filter = filter != null ? filter : TextureFilter.LINEAR;
        this.wrapS = wrapS != null ? wrapS : TextureWrap.CLAMP_TO_EDGE;
        this.wrapT = wrapT != null ? wrapT : TextureWrap.CLAMP_TO_EDGE;
        view = new WGPUTextureViewHandle(this);
    }

    WGPUTexture nativeTexture() {
        return allocation.nativeTexture();
    }

    WGPUTextureView nativeView() {
        return allocation.nativeView();
    }

    WGPUSampler nativeSampler() {
        return allocation.nativeSampler();
    }

    WGPUTextureAllocation allocation() {
        return allocation;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    void replaceAllocation(WGPUTextureAllocation replacement) {
        if (replacement == null || replacement.resourceDomain() != resourceDomain) {
            throw new FdxException("Replacement WGPU texture allocation is incompatible");
        }
        WGPUTextureAllocation previous = allocation;
        allocation = replacement;
        previous.retire();
    }

    String label() {
        return label;
    }

    int mipLevelCount() {
        return mipLevelCount;
    }

    TextureFilter filter() {
        return filter;
    }

    TextureWrap wrapS() {
        return wrapS;
    }

    TextureWrap wrapT() {
        return wrapT;
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

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        allocation.retire();
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
