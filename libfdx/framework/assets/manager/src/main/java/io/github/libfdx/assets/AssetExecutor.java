package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;

/**
 * Application-owned executor for CPU preparation and file acquisition. Asset
 * managers borrow it; dispose it after disposing those managers. Tasks must not
 * create or access graphics resources or block waiting for another asset.
 */
public interface AssetExecutor extends Disposable {
    /**
     * Attempts to accept a task without blocking the caller.
     *
     * @param task the non-null task; an accepted task runs exactly once
     * @return true if accepted; false if temporarily full (the task has not run)
     * @throws RuntimeException if the executor cannot accept future work
     */
    boolean submit(Runnable task);
}
