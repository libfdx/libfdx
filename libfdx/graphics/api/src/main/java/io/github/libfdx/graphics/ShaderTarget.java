package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

import java.util.Locale;

public enum ShaderTarget {
    WEBGPU_WGSL,
    WGPU_WGSL,
    WEBGL_GLSL_ES,
    GLES_GLSL_ES,
    OPENGL_GLSL,
    VULKAN_SPIRV,
    METAL_MSL,
    DIRECTX_HLSL;

    public static ShaderTarget forProvider(ProviderId providerId) {
        if (providerId == null) {
            throw new FdxException("Shader provider id cannot be null");
        }
        return forProvider(providerId.value());
    }

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
