package io.github.libfdx.runtime.core.shader;

import io.github.libfdx.core.FdxFuture;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeShaderCompilerAsyncTest {
    private static final RuntimeShaderCompileRequest INPUT = RuntimeShaderCompileRequest
            .builder("source", RuntimeShaderCompileTarget.WGPU_WGSL).build();

    @Test
    void defaultCompilationWaitsForExecutorAndPropagatesFailures() {
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        int[] calls = {0};
        RuntimeShaderCompiler compiler = request -> { calls[0]++; throw new IllegalStateException("invalid"); };
        FdxFuture<RuntimeShaderCompileResult> future = compiler.compileAsync(INPUT, work::add);
        assertFalse(future.isDone());
        assertEquals(0, calls[0]);
        work.remove().run();
        assertTrue(future.isFailed());
        assertEquals(1, calls[0]);
        var rejected = compiler.compileAsync(INPUT, ignored -> { throw new RejectedExecutionException(); });
        assertTrue(rejected.isFailed());
        assertEquals(1, calls[0]);
    }
}
