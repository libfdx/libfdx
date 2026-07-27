package io.github.libfdx.graphics.shader.target;

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
}
