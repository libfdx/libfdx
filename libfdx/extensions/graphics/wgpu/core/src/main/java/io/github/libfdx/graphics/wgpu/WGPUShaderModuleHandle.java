package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUShaderModule;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;

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

    @Override
    public ShaderLanguage language() {
        return language;
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        nativeModule.release();
        nativeModule.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
