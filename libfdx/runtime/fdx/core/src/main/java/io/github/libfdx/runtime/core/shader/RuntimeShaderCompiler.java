package io.github.libfdx.runtime.core.shader;

/**
 * Compiles WGSL into runtime shader outputs for providers that need translation.
 *
 * @author xpenatan
 */
public interface RuntimeShaderCompiler {
    /**
     * Compiles a shader.
     *
     * @param request the request
     * @return the result
     */
    RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request);
}
