package io.github.libfdx.graphics.wgpu;

/**
 * Aggregates failures while releasing a group of native WGPU handles.
 *
 * <p>This helper is reserved for resize, construction rollback, and disposal paths. Frame submission uses the
 * allocation-free static failure helpers directly.</p>
 */
final class WGPUCleanup {
    private Throwable firstFailure;

    void run(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error failure) {
            firstFailure = merge(firstFailure, failure);
        }
    }

    void throwIfFailed() {
        rethrow(firstFailure);
    }

    static Throwable merge(Throwable firstFailure, Throwable failure) {
        if (firstFailure == null) {
            return failure;
        }
        if (firstFailure != failure) {
            firstFailure.addSuppressed(failure);
        }
        return firstFailure;
    }

    static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error)failure;
    }
}
