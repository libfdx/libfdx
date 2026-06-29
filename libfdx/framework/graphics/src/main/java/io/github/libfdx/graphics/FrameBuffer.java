package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

import java.nio.ByteBuffer;

/**
 * Defines the contract for frame buffer implementations.
 *
 * @author xpenatan
 */
public interface FrameBuffer extends ProviderHandle {
    /**
     * Returns the color attachment.
     *
     * @return the color attachment
     */
    TextureView colorAttachment();

    /**
     * Returns the format.
     *
     * @return the format
     */
    TextureFormat format();

    /**
     * Returns the width.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the height.
     *
     * @return the height
     */
    int height();

    /**
     * Captures the current drawable as tightly packed RGBA8 pixels.
     *
     * <p>This is an end-of-frame operation. After this method succeeds, the frame that owns this framebuffer
     * is considered consumed: callers must not record more commands against it, and a later
     * {@link GraphicsAttachment#endFrame()} for the same frame may be a no-op.
     *
     * <p>The returned buffer is positioned at zero and contains {@code width() * height() * 4} bytes.
     */
    ByteBuffer readPixelsRgba8();
}
