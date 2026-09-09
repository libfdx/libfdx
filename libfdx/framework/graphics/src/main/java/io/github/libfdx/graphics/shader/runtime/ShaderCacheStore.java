package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxFuture;
import java.util.function.UnaryOperator;

/**
 * Application-owned asynchronous storage for opaque shader artifacts. Implementations do no
 * storage I/O on the submitting thread, bound individual entries and total storage, and replace
 * entries atomically. Keys are lowercase SHA-256 digests. Missing entries return null; unavailable
 * storage completes exceptionally. Returned read arrays belong to the caller. Write arrays are
 * borrowed and immutable until completion. Callbacks may run on the storage completion thread.
 *
 * <p>This contract neither owns a graphics device nor creates GPU resources. Dispose a concrete
 * store only after its preparation services have drained. Closing a store must not join workers;
 * already accepted operations must still complete. Stores can be shared across providers.</p>
 */
public interface ShaderCacheStore {
    FdxFuture<byte[]> readAsync(String key);
    FdxFuture<Void> writeAsync(String key, byte[] bytes);
    FdxFuture<Void> removeAsync(String key);

    /** Whether updateAsync serializes read/update/replacement across every connection/process
     * sharing this store, including ordinary writes, removals and budget eviction. */
    default boolean supportsAtomicUpdate() { return false; }

    /**
     * Reads the latest record and atomically replaces it with update's non-null result. The update
     * runs at most once on a storage worker while the store owns its write transaction; it receives
     * caller-owned bytes or null for a missing entry. It must not reenter this store or wait for its
     * I/O. The result must satisfy the normal record size bound. Failure leaves this entry unchanged.
     * Callers retain any resources used by update until the returned future completes. Contention
     * waits must be bounded and off the submitting/render thread. Unsupported stores fail without
     * invoking update; separate readAsync/writeAsync calls are not an implementation of this contract.
     */
    default FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> update) {
        return FdxFuture.failed(new UnsupportedOperationException("Atomic shader cache updates are unavailable"));
    }
}
