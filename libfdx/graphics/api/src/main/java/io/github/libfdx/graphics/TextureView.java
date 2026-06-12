package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for texture view implementations.
 *
 * @author xpenatan
 */
public interface TextureView extends ProviderHandle {
    /**
     * Returns the format.
     *
     * @return the format
     */
    TextureFormat format();
}
