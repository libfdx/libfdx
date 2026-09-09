package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;

/**
 * Application-owned group of asset leases, bound to one manager and its resource
 * domain. Create/use/dispose on that manager's application thread. Closing the
 * scope releases its remaining leases, including pending ones; other scopes,
 * standalone leases, direct loads, and dependency owners remain valid.
 *
 * <p>Closing is idempotent and rejects further loads. All leases become invalid
 * as closing begins. Pending cancellation callbacks run during disposal, after
 * their lease has released ownership. Cleanup continues if a resource disposer
 * or callback throws, then propagates the first error with later errors suppressed.
 * The manager also closes all its scopes when disposed.</p>
 */
public interface AssetScope extends Disposable {
    /**
     * Acquires a new lease belonging to this scope. Compatible requests share
     * the cached entry but each call creates an independent lease. Releasing
     * the returned lease early removes it from this scope.
     *
     * @param descriptor non-null descriptor in this manager's resource domain
     * @param <T> the asset type
     * @return a non-null owned lease; its result is delivered through update
     */
    <T> AssetLease<T> load(AssetDescriptor<T> descriptor);
}
