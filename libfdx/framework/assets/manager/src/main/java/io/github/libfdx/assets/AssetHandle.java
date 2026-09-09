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
     * Returns the borrowed asset while loaded, otherwise null. Do not dispose
     * it directly; its lifetime ends when the manager releases its final owner.
     *
     * @return the asset
     */
    T asset();

    /**
     * Returns the future. If its manager unloads this handle or is disposed
     * before loading completes, the future completes with failure. A successful
     * future retains its original result even after unloading; it does not keep
     * the resource alive. Use the handle's current status to check validity.
     *
     * @return the future
     */
    FdxFuture<T> future();
}
