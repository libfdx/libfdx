package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBuffer;

/**
 * Owns one native allocation used by a logical WGPU buffer handle.
 */
final class WGPUBufferAllocation extends WGPURecordedResource {
    private final WGPUBuffer nativeBuffer;

    WGPUBufferAllocation(WGPUResourceDomain resourceDomain, WGPUBuffer nativeBuffer) {
        super(resourceDomain);
        this.nativeBuffer = nativeBuffer;
    }

    WGPUBuffer nativeBuffer() {
        return nativeBuffer;
    }

    @Override
    protected void releaseNative() {
        if (nativeBuffer == null) {
            return;
        }
        WGPUCleanup cleanup = new WGPUCleanup();
        cleanup.run(() -> {
            if (nativeBuffer.isValid()) {
                nativeBuffer.destroy();
            }
        });
        cleanup.run(() -> {
            if (nativeBuffer.isValid()) {
                nativeBuffer.release();
            }
        });
        cleanup.run(nativeBuffer::dispose);
        cleanup.throwIfFailed();
    }
}
