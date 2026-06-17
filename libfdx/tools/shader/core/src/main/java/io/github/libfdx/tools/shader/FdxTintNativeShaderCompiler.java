package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;

/**
 * Compiles WGSL through a Tint bridge.
 *
 * @author xpenatan
 */
public final class FdxTintNativeShaderCompiler implements FdxShaderCompiler {
    private final FdxTintCompilerBridge bridge;

    public FdxTintNativeShaderCompiler(FdxTintCompilerBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public FdxShaderCompilerResult compile(FdxShaderCompilerRequest request) {
        if (request.target() == ShaderTarget.WEBGPU_WGSL || request.target() == ShaderTarget.WGPU_WGSL) {
            return FdxShaderCompilerResult.text(request.source());
        }
        if (request.target() == ShaderTarget.METAL_MSL || request.target() == ShaderTarget.DIRECTX_HLSL
                || request.target() == ShaderTarget.WEBGL_GLSL_ES || request.target() == ShaderTarget.GLES_GLSL_ES
                || request.target() == ShaderTarget.OPENGL_GLSL || request.target() == ShaderTarget.VULKAN_SPIRV) {
            if (request.stage() != FdxTintShaderStage.MODULE) {
                return FdxTintNativeBridgeSupport.toCompilerResult(
                        bridge.compile(FdxTintCompilerBridgeRequest.of(request, request.stage(), request.entryPoint())));
            }
            FdxTintCompilerBridgeResult vertex = bridge.compile(
                    FdxTintCompilerBridgeRequest.of(request, FdxTintShaderStage.VERTEX, "vs_main"));
            if (!vertex.success()) {
                return FdxTintNativeBridgeSupport.toCompilerResult(vertex);
            }
            FdxTintCompilerBridgeResult fragment = bridge.compile(
                    FdxTintCompilerBridgeRequest.of(request, FdxTintShaderStage.FRAGMENT, "fs_main"));
            if (!fragment.success()) {
                return FdxTintNativeBridgeSupport.toCompilerResult(fragment);
            }
            if (vertex.outputKind() == FdxTintCompilerOutput.SPIRV) {
                return FdxShaderCompilerResult.binary(FdxTintCompilerOutput.SPIRV, vertex.output());
            }
            return FdxShaderCompilerResult.text(vertex.outputText() + System.lineSeparator() + fragment.outputText());
        }
        return FdxShaderCompilerResult.failure(new FdxShaderCompilerDiagnostic[] {
                FdxShaderCompilerDiagnostic.of("Unsupported shader target: " + request.target())
        });
    }
}
