package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for texture implementations.
 *
 * @author xpenatan
 */
public interface Texture extends ProviderHandle, Disposable {
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
     * Returns the format.
     *
     * @return the format
     */
    TextureFormat format();

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    TextureUsage usage();

    /**
     * Returns the texture sample count.
     *
     * @return the sample count
     */
    default int sampleCount() {
        return 1;
    }

    /**
     * Returns the default texture view.
     *
     * @return the default texture view
     */
    default TextureView view() {
        throw new UnsupportedOperationException("Texture views are not supported by this provider");
    }
}
