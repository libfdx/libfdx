package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;

/**
 * Preserves WGSL for WGSL targets.
 *
 * @author xpenatan
 */
public final class FdxPassthroughShaderCompiler implements FdxShaderCompiler {
    @Override
    public FdxShaderCompilerResult compile(FdxShaderCompilerRequest request) {
        if (request.target() == ShaderTarget.WEBGPU_WGSL || request.target() == ShaderTarget.WGPU_WGSL) {
            return FdxShaderCompilerResult.text(request.source());
        }
        return FdxShaderCompilerResult.failure(new FdxShaderCompilerDiagnostic[] {
                FdxShaderCompilerDiagnostic.of("Passthrough compiler only supports WGSL targets: " + request.target())
        });
    }
}
