package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUShaderModule;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;

/**
 * Represents a WGPU shader module handle.
 *
 * @author xpenatan
 */
final class WGPUShaderModuleHandle implements ShaderModule {
    private final WGPUResourceDomain resourceDomain;
    private final WGPUShaderModule nativeModule;
    private final ShaderLanguage language;
    private final ShaderReflection reflection;
    private boolean disposed;

    WGPUShaderModuleHandle(WGPUResourceDomain resourceDomain, WGPUShaderModule nativeModule, ShaderLanguage language) {
        this(resourceDomain, nativeModule, language, ShaderReflection.empty());
    }

    WGPUShaderModuleHandle(WGPUResourceDomain resourceDomain, WGPUShaderModule nativeModule, ShaderLanguage language,
            ShaderReflection reflection) {
        if (resourceDomain == null) {
            throw new FdxException("WGPU shader resource domain cannot be null");
        }
        this.resourceDomain = resourceDomain;
        this.nativeModule = nativeModule;
        this.language = language;
        this.reflection = reflection != null ? reflection : ShaderReflection.empty();
    }

    WGPUShaderModule nativeModule() {
        return nativeModule;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    /**
     * Returns the language.
     *
     * @return the language
     */
    @Override
    public ShaderLanguage language() {
        return language;
    }

    @Override
    public ShaderReflection reflection() {
        return reflection;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (nativeModule != null) {
            WGPUCleanup cleanup = new WGPUCleanup();
            cleanup.run(() -> {
                if (nativeModule.isValid()) {
                    nativeModule.release();
                }
            });
            cleanup.run(nativeModule::dispose);
            cleanup.throwIfFailed();
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
