package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBackendType;

/**
 * Lists the supported WGPU backend values.
 *
 * @author xpenatan
 */
public enum WGPUBackend {
    DEFAULT,
    D3D11,
    D3D12,
    METAL,
    OPENGL,
    OPENGL_ES,
    VULKAN,
    WEBGPU,
    HEADLESS;

    /**
     * Returns the to native.
     *
     * @return the to native
     */
    public WGPUBackendType toNative() {
        switch (this) {
            case D3D11:
                return WGPUBackendType.D3D11;
            case D3D12:
                return WGPUBackendType.D3D12;
            case METAL:
                return WGPUBackendType.Metal;
            case OPENGL:
                return WGPUBackendType.OpenGL;
            case OPENGL_ES:
                return WGPUBackendType.OpenGLES;
            case VULKAN:
                return WGPUBackendType.Vulkan;
            case HEADLESS:
                return WGPUBackendType.Null;
            case WEBGPU:
                return WGPUBackendType.WebGPU;
            case DEFAULT:
            default:
                return WGPUBackendType.Undefined;
        }
    }
}
