package io.github.libfdx.runtime.core.shader;

import io.github.libfdx.core.FdxFuture;
import java.util.function.Consumer;

/**
 * Compiles WGSL into runtime shader outputs for providers that need translation.
 *
 * @author xpenatan
 */
public interface RuntimeShaderCompiler {
    /**
     * Stable identity of the actual compiler binary, transforms and result ABI, or null when
     * persistent reuse cannot be established. Native preparation workers may inspect packaging;
     * single-threaded targets must use a precomputed identity without runtime I/O.
     * A Java adapter version alone is insufficient. Unknown identity disables
     * translation persistence without preventing compilation.
     */
    default String cacheIdentity() { return null; }

    /**
     * Compiles a shader.
     *
     * @param request the request
     * @return the result
     */
    RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request);

    /**
     * Submits compilation of immutable input. The default queues
     * synchronous compilation on {@code execute}; browser implementations can send source to a
     * worker instead. The default completes on {@code execute}; asynchronous platform adapters
     * may complete on their event loop. Callers marshal continuations onto their own executor.
     * {@code execute} must remain available for fallback until completion. Never block waiting
     * on its owning thread. An inline executor makes the default implementation synchronous.
     * Results contain no native resources. Platform adapters define shutdown behavior;
     * abandoning a future does not interrupt native compilation.
     */
    default FdxFuture<RuntimeShaderCompileResult> compileAsync(RuntimeShaderCompileRequest request,
            Consumer<Runnable> execute) {
        FdxFuture<RuntimeShaderCompileResult> future = FdxFuture.pending();
        try {
            execute.accept(() -> {
                RuntimeShaderCompileResult result;
                try { result = compile(request); }
                catch (Throwable error) { future.completeExceptionally(error); return; }
                future.complete(result);
            });
        } catch (Throwable error) { future.completeExceptionally(error); }
        return future;
    }
}
