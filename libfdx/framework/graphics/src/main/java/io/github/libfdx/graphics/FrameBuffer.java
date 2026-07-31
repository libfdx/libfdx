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
     * Returns exact compatibility metadata for this borrowed framebuffer.
     *
     * @return render-pass compatibility
     */
    default RenderPassCompatibility compatibility() {
        return RenderPassCompatibility.of(RenderTargetLayout.color(format()), width(), height());
    }

    /**
     * Returns whether this framebuffer supports RGBA8 readback.
     *
     * <p>Provider swap-chain framebuffers normally support capture. Borrowed
     * offscreen framebuffers may return {@code false} when the portable
     * texture API cannot expose their storage for readback.</p>
     *
     * @return true when {@link #readPixelsRgba8()} is available
     */
    default boolean supportsReadPixelsRgba8() {
        return true;
    }

    /**
     * Captures the current drawable as tightly packed RGBA8 pixels.
     *
     * <p>This is an end-of-frame operation. After this method succeeds, the frame that owns this framebuffer
     * is considered consumed: callers must not record more commands against it, and a later
     * {@link GraphicsAttachment#endFrame()} for the same frame may be a no-op.
     *
     * <p>Callers must check {@link #supportsReadPixelsRgba8()} before invoking
     * this method. The returned buffer is positioned at zero and contains
     * {@code width() * height() * 4} bytes.
     */
    ByteBuffer readPixelsRgba8();
}
