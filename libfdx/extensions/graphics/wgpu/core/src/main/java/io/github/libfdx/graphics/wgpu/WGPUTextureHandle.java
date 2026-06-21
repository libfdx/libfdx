package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;

/**
 * Represents a WGPU texture handle.
 *
 * @author xpenatan
 */
final class WGPUTextureHandle implements Texture {
    private final WGPUTexture nativeTexture;
    private final WGPUTextureView nativeView;
    private final WGPUSampler nativeSampler;
    private final WGPUTextureViewHandle view;
    private final int width;
    private final int height;
    private final int mipLevelCount;
    private final TextureFormat format;
    private final TextureUsage usage;
    private WGPUBindGroupLayout cachedBindGroupLayout;
    private WGPUBindGroup cachedBindGroup;
    private boolean disposed;

    WGPUTextureHandle(WGPUTexture nativeTexture, WGPUTextureView nativeView, WGPUSampler nativeSampler,
            int width, int height, int mipLevelCount, TextureFormat format, TextureUsage usage) {
        this.nativeTexture = nativeTexture;
        this.nativeView = nativeView;
        this.nativeSampler = nativeSampler;
        this.width = width;
        this.height = height;
        this.mipLevelCount = Math.max(1, mipLevelCount);
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        view = new WGPUTextureViewHandle(nativeView, this.format, width, height);
    }

    WGPUTexture nativeTexture() {
        return nativeTexture;
    }

    WGPUTextureView nativeView() {
        return nativeView;
    }

    WGPUSampler nativeSampler() {
        return nativeSampler;
    }

    int mipLevelCount() {
        return mipLevelCount;
    }

    WGPUBindGroup bindGroup(WGPUContext context, WGPUBindGroupLayout layout) {
        if (cachedBindGroup != null && cachedBindGroupLayout == layout && cachedBindGroup.isValid()) {
            return cachedBindGroup;
        }
        releaseCachedBindGroup();

        WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
        WGPUBindGroupEntry textureEntry = WGPUBindGroupEntry.obtain();
        textureEntry.setNextInChain(WGPUChainedStruct.NULL);
        textureEntry.setBinding(0);
        textureEntry.setTextureView(nativeView);
        entries.push_back(textureEntry);

        WGPUBindGroupEntry samplerEntry = WGPUBindGroupEntry.obtain();
        samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
        samplerEntry.setBinding(1);
        samplerEntry.setSampler(nativeSampler);
        entries.push_back(samplerEntry);

        WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx texture bind group");
        descriptor.setLayout(layout);
        descriptor.setEntries(entries);

        cachedBindGroup = new WGPUBindGroup();
        context.nativeDevice().createBindGroup(descriptor, cachedBindGroup);
        cachedBindGroupLayout = layout;
        return cachedBindGroup;
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
        releaseCachedBindGroup();
        nativeSampler.release();
        nativeSampler.dispose();
        nativeView.release();
        nativeView.dispose();
        nativeTexture.destroy();
        nativeTexture.release();
        nativeTexture.dispose();
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

    private void releaseCachedBindGroup() {
        if (cachedBindGroup != null) {
            cachedBindGroup.release();
            cachedBindGroup.dispose();
            cachedBindGroup = null;
            cachedBindGroupLayout = null;
        }
    }
}
