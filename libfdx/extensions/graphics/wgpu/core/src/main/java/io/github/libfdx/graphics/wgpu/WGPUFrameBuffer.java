package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

import java.nio.ByteBuffer;

final class WGPUFrameBuffer implements FrameBuffer {
    private final WGPUContext context;
    private final TextureView colorAttachment;

    WGPUFrameBuffer(WGPUContext context, TextureView colorAttachment) {
        this.context = context;
        this.colorAttachment = colorAttachment;
    }

    @Override
    public TextureView colorAttachment() {
        return colorAttachment;
    }

    @Override
    public TextureFormat format() {
        return context.surfaceFormat();
    }

    @Override
    public int width() {
        return context.width();
    }

    @Override
    public int height() {
        return context.height();
    }

    @Override
    public ByteBuffer readPixelsRgba8() {
        return context.readPixelsRgba8();
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
