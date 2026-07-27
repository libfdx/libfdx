package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for graphics frame implementations.
 *
 * @author xpenatan
 */
public interface GraphicsFrame extends ProviderHandle {
    /**
     * Returns the command encoder.
     *
     * @return the command encoder
     */
    CommandEncoder commandEncoder();

    /**
     * Returns the frame buffer.
     *
     * @return the frame buffer
     */
    FrameBuffer frameBuffer();

    /**
     * Returns the color attachment.
     *
     * @return the color attachment
     */
    TextureView colorAttachment();

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
     * Returns exact compatibility metadata for the frame's render target.
     *
     * @return render-pass compatibility
     */
    default RenderPassCompatibility compatibility() {
        return frameBuffer().compatibility();
    }
}
