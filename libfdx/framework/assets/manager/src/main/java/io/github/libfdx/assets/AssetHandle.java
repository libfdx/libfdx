package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;

/**
 * Defines a typed handle for asset state.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface AssetHandle<T> {
    /**
     * Returns the descriptor.
     *
     * @return the descriptor
     */
    AssetDescriptor<T> descriptor();

    /**
     * Returns the status.
     *
     * @return the status
     */
    AssetStatus status();

    /**
     * Returns whether loaded is enabled or true.
     *
     * @return true if loaded is enabled or true; false otherwise
     */
    boolean isLoaded();

    /**
     * Returns the asset.
     *
     * @return the asset
     */
    T asset();

    /**
     * Returns the future.
     *
     * @return the future
     */
    FdxFuture<T> future();
}
