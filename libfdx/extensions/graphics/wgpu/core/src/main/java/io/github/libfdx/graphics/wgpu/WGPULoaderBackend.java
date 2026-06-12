package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.JWebGPUBackend;

/**
 * Lists the supported WGPU loader backend values.
 *
 * @author xpenatan
 */
public enum WGPULoaderBackend {
    WGPU,
    DAWN;

    /**
     * Returns the to native.
     *
     * @return the to native
     */
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
