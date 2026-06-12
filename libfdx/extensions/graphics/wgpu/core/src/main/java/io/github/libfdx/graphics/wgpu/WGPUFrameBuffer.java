package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;

import java.nio.ByteBuffer;

/**
 * Represents a WGPU frame buffer.
 *
 * @author xpenatan
 */
final class WGPUFrameBuffer implements FrameBuffer {
    private final WGPUContext context;
    private final TextureView colorAttachment;

    WGPUFrameBuffer(WGPUContext context, TextureView colorAttachment) {
        this.context = context;
        this.colorAttachment = colorAttachment;
    }

    /**
     * Returns the color attachment.
     *
     * @return the color attachment
     */
    @Override
    public TextureView colorAttachment() {
        return colorAttachment;
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    @Override
    public TextureFormat format() {
        return context.surfaceFormat();
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    @Override
    public int width() {
        return context.width();
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    @Override
    public int height() {
        return context.height();
    }

    /**
     * Returns the read pixels RGBA8.
     *
     * @return the read pixels RGBA8
     */
    @Override
    public ByteBuffer readPixelsRgba8() {
        return context.readPixelsRgba8();
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
