package io.github.libfdx.graphics.shader.target;

/**
 * Built-in shader target identities.
 *
 * @author xpenatan
 */
public final class ShaderTargets {
    public static final ShaderTargetId WEBGPU_WGSL = ShaderTargetId.of("webgpu-wgsl");
    public static final ShaderTargetId WGPU_WGSL = ShaderTargetId.of("wgpu-wgsl");
    public static final ShaderTargetId WEBGL_GLSL_ES = ShaderTargetId.of("webgl-glsl-es");
    public static final ShaderTargetId GLES_GLSL_ES = ShaderTargetId.of("gles-glsl-es");
    public static final ShaderTargetId OPENGL_GLSL = ShaderTargetId.of("opengl-glsl");
    public static final ShaderTargetId VULKAN_SPIRV = ShaderTargetId.of("vulkan-spirv");
    public static final ShaderTargetId METAL_MSL = ShaderTargetId.of("metal-msl");
    public static final ShaderTargetId DIRECTX_HLSL = ShaderTargetId.of("directx-hlsl");

    private static final ShaderTargetId[] STANDARD = {
            DIRECTX_HLSL,
            GLES_GLSL_ES,
            METAL_MSL,
            OPENGL_GLSL,
            VULKAN_SPIRV,
            WEBGL_GLSL_ES,
            WEBGPU_WGSL,
            WGPU_WGSL
    };

    private ShaderTargets() {
    }

    /**
     * Returns the built-in targets in stable ID order.
     *
     * @return the targets
     */
    public static ShaderTargetId[] standard() {
        return STANDARD.clone();
    }
}
