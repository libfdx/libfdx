package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUShaderModule;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;

/**
 * Represents a WGPU shader module handle.
 *
 * @author xpenatan
 */
final class WGPUShaderModuleHandle implements ShaderModule {
    private final WGPUShaderModule nativeModule;
    private final ShaderLanguage language;
    private boolean disposed;

    WGPUShaderModuleHandle(WGPUShaderModule nativeModule, ShaderLanguage language) {
        this.nativeModule = nativeModule;
        this.language = language;
    }

    WGPUShaderModule nativeModule() {
        return nativeModule;
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
        nativeModule.release();
        nativeModule.dispose();
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
