package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureView;

/**
 * Validates WGPU handles without invoking the native API.
 */
final class WGPUResources {
    private WGPUResources() {
    }

    static WGPUBufferHandle requireBuffer(Buffer value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUBufferHandle handle) || handle.resourceDomain() != domain) {
            throw new FdxException(name + " belongs to an incompatible WGPU resource domain");
        }
        if (handle.isDisposed()) {
            throw new FdxException(name + " has been disposed");
        }
        return handle;
    }

    static WGPUTextureHandle requireTexture(Texture value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUTextureHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static WGPUSamplerHandle requireSampler(Sampler value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUSamplerHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
        return handle;
    }

    static WGPUShaderModuleHandle requireShaderModule(ShaderModule value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUShaderModuleHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
        return handle;
    }

    static WGPURenderPipelineHandle requirePipeline(RenderPipeline value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPURenderPipelineHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static WGPUComputePipelineHandle requireComputePipeline(
            ComputePipeline value, WGPUResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUComputePipelineHandle handle)
                || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
        return handle;
    }

    static WGPUTextureViewHandle requireTextureView(TextureView value, WGPUContext context, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof WGPUTextureViewHandle handle)
                || handle.resourceDomain() != context.resourceDomain()) {
            throw incompatible(name);
        }
        if (handle.textureHandle() != null) {
            requireUsable(handle.textureHandle(), context.resourceDomain(), name);
        } else if (handle.frameOwner() != context) {
            throw new FdxException(name + " belongs to a different WGPU frame context");
        }
        return handle;
    }

    static void requireUsable(WGPUBufferHandle handle, WGPUResourceDomain domain, String name) {
        if (handle == null || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    static void requireUsable(WGPUTextureHandle handle, WGPUResourceDomain domain, String name) {
        if (handle == null || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    static void requireUsable(WGPURenderPipelineHandle handle, WGPUResourceDomain domain, String name) {
        if (handle == null || handle.resourceDomain() != domain) {
            throw incompatible(name);
        }
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    private static FdxException incompatible(String name) {
        return new FdxException(name + " belongs to an incompatible WGPU resource domain");
    }

    private static FdxException disposed(String name) {
        return new FdxException(name + " has been disposed");
    }
}
