package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;

/**
 * Application-owned asset loading driven by updates on the application thread.
 * Requests may remain pending across frames, including while downloading input.
 * Continue rendering and call {@link #update(int, long)} each frame; inspect the
 * returned handles or futures before using an asset. Preloading is optional.
 *
 * @author xpenatan
 */
public interface AssetManager extends Disposable {
    /**
     * Creates an application-owned group of leases in this manager's resource
     * domain. Disposing the scope releases only its own ownership; disposing
     * this manager closes every scope.
     *
     * @return a non-null empty scope
     */
    AssetScope createScope();

    /**
     * Acquires an independent application-owned lease outside a scope. Repeated
     * calls return distinct leases while sharing a compatible cached entry.
     * Release each lease with dispose(); unload(path) does not release it.
     * Completion notifications participate in the update budget.
     *
     * @param descriptor the non-null asset descriptor
     * @param <T> the asset type
     * @return a non-null owned lease
     */
    <T> AssetLease<T> acquire(AssetDescriptor<T> descriptor);

    /**
     * Requests manager-owned data and returns a borrowed handle. Repeating a
     * compatible request shares the entry and does not add another direct owner.
     * Release direct ownership with {@link #unload(String)}. Assets retained as
     * dependencies or leases remain alive until their final owner releases them.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
    <T> AssetHandle<T> load(AssetDescriptor<T> descriptor);

    /**
     * Returns the load result with the same ownership as {@link #load}.
     * Background execution depends on the configured executor and loader;
     * the name alone does not make legacy synchronous loaders run on a worker.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
    <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor);

    /**
     * Processes pending completion work and reports whether loading has finished.
     *
     * <p>A finished load may have failed. Inspect each asset handle's status or
     * future to distinguish success from failure.</p>
     *
     * @return true when no asset work or managed notifications remain pending,
     * including when the manager is empty; false while work remains
     */
    boolean update();

    /**
     * Processes queued work within both limits, checking time between steps.
     * A step cannot be interrupted. Zero in either limit performs no queued work;
     * negative limits are invalid. Pending dependencies do not block ready work.
     * Finalization, worker-result delivery, dependency readiness, and lease
     * notifications share the same budget. Recursive update calls
     * are invalid.
     *
     * @param maxTasks the maximum number of attempted queue steps
     * @param maxNanos the elapsed-time limit in nanoseconds
     * @return true when no queued/loading work remains, including failed loads
     */
    boolean update(int maxTasks, long maxNanos);

    /**
     * Returns the loaded asset at the given path.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the loaded asset
     */
    <T> T get(String path, Class<T> type);

    /**
     * Finds a matching value.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the matching value, or null if none is available
     */
    <T> T find(String path, Class<T> type);

    /**
     * Unloads the resource at the given path. If loading is still pending, the
     * matching handle becomes unloaded and its future completes with failure
     * after its final owner releases it. This releases direct
     * application ownership; scoped/standalone leases and dependencies retained
     * by another asset stay valid. It never releases ownership held by a scope.
     *
     * @param path the asset or file path
     */
    void unload(String path);

    /**
     * Registers an asset loader for a Java type.
     *
     * @param type the expected Java type
     * @param loader the loader to register
     */
    void registerLoader(Class<?> type, AssetLoader<?> loader);
}
