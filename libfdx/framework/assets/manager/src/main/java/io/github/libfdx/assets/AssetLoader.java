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
     * Starts dependency discovery and returns a future for an owned result.
     * The default manager invokes this method on the application thread. Use
     * context.readBytes/async for queued acquisition and preparation, declare
     * dependencies through the context, and use completeOnUpdate for resource
     * creation. Arbitrary inline work in this method is outside update budgets.
     * A loader must release resources it creates before throwing or failing.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    FdxFuture<T> load(AssetLoadContext context, AssetDescriptor<T> descriptor);
}
