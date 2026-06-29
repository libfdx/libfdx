package io.github.libfdx.runtime.core.shader;

/**
 * Lists runtime shader compiler targets.
 *
 * @author xpenatan
 */
public enum RuntimeShaderCompileTarget {
    WEBGPU_WGSL,
    WGPU_WGSL,
    WEBGL_GLSL_ES,
    GLES_GLSL_ES,
    OPENGL_GLSL,
    VULKAN_SPIRV,
    METAL_MSL,
    DIRECTX_HLSL
}
