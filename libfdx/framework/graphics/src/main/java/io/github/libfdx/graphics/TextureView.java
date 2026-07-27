package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for texture view implementations.
 *
 * @author xpenatan
 */
public interface TextureView extends ProviderHandle {
    /**
     * Returns the view width when known, or zero for an externally owned view
     * whose dimensions must be supplied through render-pass compatibility.
     *
     * @return width in pixels, or zero
     */
    default int width() {
        return 0;
    }

    /**
     * Returns the view height when known, or zero for an externally owned view
     * whose dimensions must be supplied through render-pass compatibility.
     *
     * @return height in pixels, or zero
     */
    default int height() {
        return 0;
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    TextureFormat format();

    /**
     * Returns the attachment sample count.
     *
     * @return the sample count
     */
    default int sampleCount() {
        return 1;
    }
}
