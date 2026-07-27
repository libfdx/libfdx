package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;

/**
 * Exact built-in provider consumer environments.
 *
 * @author xpenatan
 */
public final class ShaderTargetEnvironments {
    public static final ShaderTargetEnvironment WEBGPU_WGSL_1 = ShaderTargetEnvironment.builder(
                    "webgpu-wgsl-1", ShaderTargets.WEBGPU_WGSL, ShaderArtifactFormats.WGSL_TEXT)
            .consumer("webgpu", "1")
            .compiler("browser-webgpu", "wgsl")
            .build();
    public static final ShaderTargetEnvironment WGPU_WGSL_1 = ShaderTargetEnvironment.builder(
                    "wgpu-wgsl-1", ShaderTargets.WGPU_WGSL, ShaderArtifactFormats.WGSL_TEXT)
            .consumer("wgpu-native", "1")
            .compiler("wgpu-native", "wgsl")
            .build();
    public static final ShaderTargetEnvironment WEBGL2_GLSL_ES_300 = ShaderTargetEnvironment.builder(
                    "webgl2-glsl-es-300", ShaderTargets.WEBGL_GLSL_ES, ShaderArtifactFormats.GLSL_ES_TEXT)
            .consumer("webgl", "2")
            .compiler("browser-webgl", "glsl-es-300")
            .options(ShaderTargetOptions.builder().option("glsl-es.version", "300").build())
            .build();
    public static final ShaderTargetEnvironment GLES3_GLSL_ES_300 = ShaderTargetEnvironment.builder(
                    "gles3-glsl-es-300", ShaderTargets.GLES_GLSL_ES, ShaderArtifactFormats.GLSL_ES_TEXT)
            .consumer("opengl-es", "3.0")
            .compiler("gles-driver", "glsl-es-300")
            .options(ShaderTargetOptions.builder().option("glsl-es.version", "300").build())
            .build();
    public static final ShaderTargetEnvironment OPENGL_33_GLSL_330 = ShaderTargetEnvironment.builder(
                    "opengl-3.3-glsl-330", ShaderTargets.OPENGL_GLSL, ShaderArtifactFormats.GLSL_TEXT)
            .consumer("opengl", "3.3")
            .compiler("opengl-driver", "glsl-330")
            .options(ShaderTargetOptions.builder().option("glsl.version", "330").build())
            .build();
    public static final ShaderTargetEnvironment VULKAN_1_0_SPIRV_1_0 = ShaderTargetEnvironment.builder(
                    "vulkan-1.0-spirv-1.0", ShaderTargets.VULKAN_SPIRV, ShaderArtifactFormats.SPIRV_BINARY)
            .consumer("vulkan", "1.0")
            .compiler("vulkan-driver", "spirv-1.0")
            .build();
    public static final ShaderTargetEnvironment IOS_METAL_2_MSL_2 = ShaderTargetEnvironment.builder(
                    "ios-metal-2-msl-2", ShaderTargets.METAL_MSL, ShaderArtifactFormats.MSL_TEXT)
            .consumer("ios-metal", "2")
            .compiler("metal", "msl-2")
            .build();
    public static final ShaderTargetEnvironment D3D12_FXC_SM_5_1 = ShaderTargetEnvironment.builder(
                    "d3d12-fxc-sm-5.1", ShaderTargets.DIRECTX_HLSL, ShaderArtifactFormats.HLSL_TEXT)
            .consumer("direct3d12", "12")
            .compiler("fxc", "5.1")
            .build();
    public static final ShaderTargetEnvironment D3D12_DXC_SM_6_0 = ShaderTargetEnvironment.builder(
                    "d3d12-dxc-sm-6.0", ShaderTargets.DIRECTX_HLSL, ShaderArtifactFormats.HLSL_TEXT)
            .consumer("direct3d12", "12")
            .compiler("dxc", "6.0")
            .build();

    private static final ShaderTargetEnvironment[] STANDARD = {
            D3D12_FXC_SM_5_1,
            GLES3_GLSL_ES_300,
            IOS_METAL_2_MSL_2,
            OPENGL_33_GLSL_330,
            VULKAN_1_0_SPIRV_1_0,
            WEBGL2_GLSL_ES_300,
            WEBGPU_WGSL_1,
            WGPU_WGSL_1
    };

    private ShaderTargetEnvironments() {
    }

    /**
     * Returns the default built-in environment for a target.
     *
     * @param target the target
     * @return the environment
     */
    public static ShaderTargetEnvironment forTarget(ShaderTargetId target) {
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        for (ShaderTargetEnvironment environment : STANDARD) {
            if (environment.target().equals(target)) {
                return environment;
            }
        }
        throw new FdxException("No built-in shader environment is registered for target " + target);
    }

    /**
     * Returns built-in environments in stable ID order.
     *
     * @return the environments
     */
    public static ShaderTargetEnvironment[] standard() {
        return STANDARD.clone();
    }
}
