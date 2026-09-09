package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;

/**
 * Provider-owned asynchronous work, polled only by its preparation service on the application
 * thread. Polling, finish(), cancel(), and dispose() must not wait for compilation. Submission
 * follows ShaderProvider.beginPreparation's advertised execution policy. Implementations isolate
 * worker inputs/native ownership and publish completion with the required memory visibility.
 *
 * <p>After isDone() returns true, finish() is called once to transfer resource ownership, or to
 * throw the preparation failure. dispose() follows finish(), including after failure. cancel()
 * is a request, not permission to free inputs still used by native work. The service keeps polling
 * cancelled operations until they finish before disposing them.</p>
 */
public interface ShaderPreparationOperation extends Disposable {
    /** Optional direct instrumentation. Null reports phase/cache timings as unavailable. */
    default ShaderPreparationTrace trace() { return null; }
    /**
     * Advances stages that require blocking calls on the application thread. Only
     * {@link ShaderPreparation#updateLoading()} invokes this hook, while the operation is
     * active. Never wait here for workers or cache I/O: leave the operation pending until
     * those inputs arrive. Capture failures for finish(), just as for asynchronous work.
     * Providers that can prepare without blocking owner calls need no loading advance.
     */
    default void advanceLoading() { }

    boolean isDone();
    ShaderPreparationPhase phase();
    ShaderPreparedResult finish();
    void cancel();
}
