package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureView;

/**
 * Validates GL resource handles without invoking a provider or native API.
 */
final class GLResources {
    private GLResources() {
    }

    static GLBufferHandle requireBuffer(Buffer value, GLResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof GLBufferHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name, domain);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static GLTextureHandle requireTexture(Texture value, GLResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof GLTextureHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name, domain);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static GLShaderModuleHandle requireShaderModule(ShaderModule value, GLResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof GLShaderModuleHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name, domain);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static GLRenderPipelineHandle requirePipeline(RenderPipeline value, GLResourceDomain domain, String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof GLRenderPipelineHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name, domain);
        }
        requireUsable(handle, domain, name);
        return handle;
    }

    static GLTextureViewHandle requireTextureView(TextureView value, GLResourceDomain domain, Object frameOwner,
            String name) {
        if (value == null) {
            throw new FdxException(name + " cannot be null");
        }
        if (!(value instanceof GLTextureViewHandle handle) || handle.resourceDomain() != domain) {
            throw incompatible(name, domain);
        }
        if (handle.textureBacked()) {
            requireUsable(handle.textureHandle(), domain, name);
        } else if (handle.frameOwner() != frameOwner) {
            throw new FdxException(name + " belongs to a different GL frame attachment");
        }
        return handle;
    }

    static void requireUsable(GLBufferHandle handle, GLResourceDomain domain, String name) {
        requireDomain(handle.resourceDomain(), domain, name);
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    static void requireUsable(GLTextureHandle handle, GLResourceDomain domain, String name) {
        requireDomain(handle.resourceDomain(), domain, name);
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    static void requireUsable(GLShaderModuleHandle handle, GLResourceDomain domain, String name) {
        requireDomain(handle.resourceDomain(), domain, name);
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    static void requireUsable(GLRenderPipelineHandle handle, GLResourceDomain domain, String name) {
        requireDomain(handle.resourceDomain(), domain, name);
        if (handle.isDisposed()) {
            throw disposed(name);
        }
    }

    private static void requireDomain(GLResourceDomain actual, GLResourceDomain expected, String name) {
        if (actual != expected) {
            throw incompatible(name, expected);
        }
    }

    private static FdxException incompatible(String name, GLResourceDomain domain) {
        return new FdxException(name + " is not compatible with GL resource domain " + domain.providerId());
    }

    private static FdxException disposed(String name) {
        return new FdxException(name + " has been disposed");
    }
}
