package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

import java.util.Locale;

/**
 * Lists the supported shader target values.
 *
 * @author xpenatan
 */
public enum ShaderTarget {
    WEBGPU_WGSL(ShaderTargets.WEBGPU_WGSL, ShaderArtifactFormats.WGSL_TEXT,
            ShaderTargetEnvironments.WEBGPU_WGSL_1),
    WGPU_WGSL(ShaderTargets.WGPU_WGSL, ShaderArtifactFormats.WGSL_TEXT,
            ShaderTargetEnvironments.WGPU_WGSL_1),
    WEBGL_GLSL_ES(ShaderTargets.WEBGL_GLSL_ES, ShaderArtifactFormats.GLSL_ES_TEXT,
            ShaderTargetEnvironments.WEBGL2_GLSL_ES_300),
    GLES_GLSL_ES(ShaderTargets.GLES_GLSL_ES, ShaderArtifactFormats.GLSL_ES_TEXT,
            ShaderTargetEnvironments.GLES3_GLSL_ES_300),
    OPENGL_GLSL(ShaderTargets.OPENGL_GLSL, ShaderArtifactFormats.GLSL_TEXT,
            ShaderTargetEnvironments.OPENGL_33_GLSL_330),
    VULKAN_SPIRV(ShaderTargets.VULKAN_SPIRV, ShaderArtifactFormats.SPIRV_BINARY,
            ShaderTargetEnvironments.VULKAN_1_0_SPIRV_1_0),
    METAL_MSL(ShaderTargets.METAL_MSL, ShaderArtifactFormats.MSL_TEXT,
            ShaderTargetEnvironments.IOS_METAL_2_MSL_2),
    DIRECTX_HLSL(ShaderTargets.DIRECTX_HLSL, ShaderArtifactFormats.HLSL_TEXT,
            ShaderTargetEnvironments.D3D12_FXC_SM_5_1);

    private final ShaderTargetId id;
    private final ShaderArtifactFormat format;
    private final ShaderTargetEnvironment environment;

    ShaderTarget(ShaderTargetId id, ShaderArtifactFormat format, ShaderTargetEnvironment environment) {
        this.id = id;
        this.format = format;
        this.environment = environment;
    }

    /**
     * Returns the stable extensible target identity.
     *
     * @return the target ID
     */
    public ShaderTargetId id() {
        return id;
    }

    /**
     * Returns the built-in artifact format.
     *
     * @return the format
     */
    public ShaderArtifactFormat format() {
        return format;
    }

    /**
     * Returns the default exact consumer environment.
     *
     * @return the environment
     */
    public ShaderTargetEnvironment environment() {
        return environment;
    }

    /**
     * Resolves a source-compatible enum value from a stable target ID.
     *
     * @param id the target ID
     * @return the enum value
     */
    public static ShaderTarget fromId(ShaderTargetId id) {
        if (id == null) {
            throw new FdxException("Shader target id cannot be null");
        }
        for (ShaderTarget target : values()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        throw new FdxException("Shader target ID is not a built-in enum target: " + id);
    }

    /**
     * Creates a shader target.
     *
     * @param providerId the provider ID
     * @return a new shader target
     */
    public static ShaderTarget forProvider(ProviderId providerId) {
        if (providerId == null) {
            throw new FdxException("Shader provider id cannot be null");
        }
        return forProvider(providerId.value());
    }

    /**
     * Creates a shader target.
     *
     * @param providerId the provider ID
     * @return a new shader target
     */
    public static ShaderTarget forProvider(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new FdxException("Shader provider id cannot be empty");
        }
        String value = providerId.toLowerCase(Locale.ROOT);
        if ("webgpu".equals(value)) {
            return WEBGPU_WGSL;
        }
        if ("wgpu".equals(value)) {
            return WGPU_WGSL;
        }
        if ("webgl".equals(value)) {
            return WEBGL_GLSL_ES;
        }
        if ("gles".equals(value)) {
            return GLES_GLSL_ES;
        }
        if ("gl".equals(value) || "opengl".equals(value)) {
            return OPENGL_GLSL;
        }
        if ("vulkan".equals(value)) {
            return VULKAN_SPIRV;
        }
        if ("metal".equals(value)) {
            return METAL_MSL;
        }
        if ("directx".equals(value) || "d3d".equals(value) || "d3d11".equals(value) || "d3d12".equals(value)) {
            return DIRECTX_HLSL;
        }
        throw new FdxException("Unsupported shader provider id: " + providerId);
    }
}
