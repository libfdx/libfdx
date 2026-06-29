package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;

/**
 * Defines the contract for asset loader implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface AssetLoader<T> {
    /**
     * Returns the type.
     *
     * @return the type
     */
    Class<T> type();

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    FdxFuture<T> load(AssetLoadContext context, AssetDescriptor<T> descriptor);
}
