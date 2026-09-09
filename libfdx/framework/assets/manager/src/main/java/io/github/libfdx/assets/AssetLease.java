package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;

/**
 * One independently releasable ownership claim on a managed asset. The resource
 * returned by {@link #asset()} is borrowed; dispose this lease, not that resource.
 * Repeated requests may share a resource while returning distinct leases.
 *
 * <p>Dispose on the manager's application thread. Disposal is idempotent, changes
 * this lease's status to UNLOADED, and cancels its pending future inline without
 * cancelling other owners. A successful future retains its original result after
 * release and does not extend the resource's lifetime. Check {@link #isLoaded()}
 * before using the borrowed value. Closing its scope or manager also releases it.</p>
 *
 * <p>Success/failure notifications use the manager's update budget, even when the
 * shared entry was already loaded. As with FdxFuture, callbacks registered after
 * completion run immediately on the registering thread.</p>
 *
 * @param <T> the resource type
 */
public interface AssetLease<T> extends AssetHandle<T>, Disposable {
}
