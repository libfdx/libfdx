package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureView;

final class WGPUGraphicsFrame implements GraphicsFrame {
    private final WGPUContext context;
    private final CommandEncoder commandEncoder;
    private final FrameBuffer frameBuffer;
    private final TextureView colorAttachment;

    WGPUGraphicsFrame(WGPUContext context, CommandEncoder commandEncoder, FrameBuffer frameBuffer,
            TextureView colorAttachment) {
        this.context = context;
        this.commandEncoder = commandEncoder;
        this.frameBuffer = frameBuffer;
        this.colorAttachment = colorAttachment;
    }

    @Override
    public CommandEncoder commandEncoder() {
        return commandEncoder;
    }

    @Override
    public FrameBuffer frameBuffer() {
        return frameBuffer;
    }

    @Override
    public TextureView colorAttachment() {
        return colorAttachment;
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
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
