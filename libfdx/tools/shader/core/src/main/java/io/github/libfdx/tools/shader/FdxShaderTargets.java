package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;

/**
 * Converts shader targets to native ABI values.
 *
 * @author xpenatan
 */
public final class FdxShaderTargets {
    private FdxShaderTargets() {
    }

    public static int nativeTarget(ShaderTarget target) {
        switch (target) {
            case WEBGPU_WGSL:
                return 0;
            case WGPU_WGSL:
                return 1;
            case WEBGL_GLSL_ES:
                return 2;
            case GLES_GLSL_ES:
                return 3;
            case OPENGL_GLSL:
                return 4;
            case VULKAN_SPIRV:
                return 5;
            case METAL_MSL:
                return 6;
            case DIRECTX_HLSL:
                return 7;
            default:
                return 0;
        }
    }

    public static int nativeStage(FdxTintShaderStage stage) {
        if (stage == FdxTintShaderStage.VERTEX) {
            return 1;
        }
        if (stage == FdxTintShaderStage.FRAGMENT) {
            return 2;
        }
        return 0;
    }

    public static FdxTintCompilerOutput outputKind(int value) {
        if (value == 1) {
            return FdxTintCompilerOutput.TEXT;
        }
        if (value == 2) {
            return FdxTintCompilerOutput.SPIRV;
        }
        return FdxTintCompilerOutput.NONE;
    }
}
