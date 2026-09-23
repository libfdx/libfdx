package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;

/**
 * Call-local error routing for audited synchronous native creation calls. In pinned
 * wgpu-native and Dawn, creation validation callbacks run inline before these calls return.
 * A device shares this router across its contexts; each calling thread has its own scope.
 * Async pipeline results use their own callback; browser callbacks, device loss and errors
 * outside a synchronous creation scope are not captured.
 * This does not make the binding's mutable descriptors safe for concurrent creation.
 */
final class WGPUCreationErrors {
    private final ThreadLocal<Scope> current = new ThreadLocal<>();

    Scope begin(String operation) {
        if (current.get() != null) throw new IllegalStateException("Nested WGPU creation scope");
        Scope scope = new Scope(operation);
        current.set(scope);
        return scope;
    }

    /** Called from native callbacks; records diagnostics without throwing across the binding. */
    boolean capture(String message) {
        Scope scope = current.get();
        if (scope == null) return false;
        if (scope.error == null) scope.error = message;
        return true;
    }

    static void check(Scope scope) {
        if (scope != null && scope.error != null) {
            throw new FdxException("Could not create WGPU " + scope.operation + ": " + scope.error);
        }
    }

    final class Scope implements AutoCloseable {
        private final String operation;
        private String error;

        private Scope(String operation) { this.operation = operation; }

        @Override
        public void close() {
            if (current.get() != this) throw new IllegalStateException("WGPU creation scope closed on another thread or twice");
            current.remove();
        }
    }
}
