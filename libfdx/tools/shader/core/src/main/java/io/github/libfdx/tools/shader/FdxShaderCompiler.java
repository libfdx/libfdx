package io.github.libfdx.tools.shader;

/**
 * Compiles shader source for one target.
 *
 * @author xpenatan
 */
public interface FdxShaderCompiler {
    /**
     * Compiles a shader request.
     *
     * @param request the request
     * @return the compiler result
     */
    FdxShaderCompilerResult compile(FdxShaderCompilerRequest request);
}
