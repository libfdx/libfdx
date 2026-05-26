package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.JWebGPUBackend;

public enum WGPULoaderBackend {
    WGPU,
    DAWN;

    public JWebGPUBackend toNative() {
        switch (this) {
            case DAWN:
                return JWebGPUBackend.DAWN;
            case WGPU:
            default:
                return JWebGPUBackend.WGPU;
        }
    }
}
