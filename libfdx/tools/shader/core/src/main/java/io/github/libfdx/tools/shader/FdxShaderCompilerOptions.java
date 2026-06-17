package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;

/**
 * Describes shader compiler options.
 *
 * @author xpenatan
 */
public final class FdxShaderCompilerOptions {
    private final ShaderTarget[] targets;

    private FdxShaderCompilerOptions(ShaderTarget[] targets) {
        this.targets = targets != null ? targets.clone() : new ShaderTarget[] { ShaderTarget.WEBGPU_WGSL };
    }

    public static FdxShaderCompilerOptions of(ShaderTarget[] targets) {
        return new FdxShaderCompilerOptions(targets);
    }

    public static FdxShaderCompilerOptions defaultOptions() {
        return of(new ShaderTarget[] {
                ShaderTarget.WEBGPU_WGSL,
                ShaderTarget.WEBGL_GLSL_ES,
                ShaderTarget.VULKAN_SPIRV,
                ShaderTarget.METAL_MSL
        });
    }

    public ShaderTarget[] targets() {
        return targets.clone();
    }
}
