package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUTextureView;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

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

    @Override
    public TextureFormat format() {
        return format;
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
