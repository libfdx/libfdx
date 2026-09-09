package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import java.util.function.Consumer;

/**
 * Translates canonical WGSL to provider-ready target artifacts.
 *
 * @author xpenatan
 */
public interface ShaderTargetCompiler {
    ShaderCompilerId id();

    String version();

    ShaderTargetId[] targets();

    boolean supports(ShaderTargetCompileRequest request);

    ShaderTargetCompileResult compile(ShaderTargetCompileRequest request);

    /**
     * Schedules compilation on the provider's borrowed, bounded executor. The provider must audit
     * this compiler for its worker strategy. Completion runs on preparation/storage threads;
     * the shader service separately publishes on its owner. No worker is created or joined here.
     */
    default FdxFuture<ShaderTargetCompileResult> compileAsync(ShaderTargetCompileRequest request,
            Consumer<Runnable> execute) {
        return ShaderCompilationTasks.submit(execute, () -> compile(request));
    }
}
