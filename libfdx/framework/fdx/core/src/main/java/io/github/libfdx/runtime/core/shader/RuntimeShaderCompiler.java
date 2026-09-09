package io.github.libfdx.runtime.core.shader;

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
}
