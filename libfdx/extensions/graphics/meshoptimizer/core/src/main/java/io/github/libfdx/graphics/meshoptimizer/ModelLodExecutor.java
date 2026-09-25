package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.core.Disposable;

/** Application-owned bounded asynchronous CPU preparation service. All methods are called on
 * its application owner, never on a render worker. One executor may serve several models.
 * Dispose after cancelling consumers; disposal never blocks or uploads graphics resources. */
public interface ModelLodExecutor extends Disposable {
    int workers();
    /** Advances transport/cancellation bookkeeping; never executes optimization inline. */
    void update();
    boolean hasCapacity();
    /** Returns null on temporary saturation. No task ran and the caller retains the input.
     * Accepted jobs run asynchronously; there is no caller-runs or synchronous fallback. */
    ModelLodJob trySubmit(ModelLodInput input, ModelLodSettings settings);
}
