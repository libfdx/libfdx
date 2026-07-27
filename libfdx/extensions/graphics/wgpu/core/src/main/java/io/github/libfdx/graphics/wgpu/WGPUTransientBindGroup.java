package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroup;

/**
 * Owns a bind group until the recording that references it is released.
 */
final class WGPUTransientBindGroup extends WGPURecordedResource {
    private final WGPUBindGroup nativeBindGroup;

    WGPUTransientBindGroup(WGPUResourceDomain resourceDomain, WGPUBindGroup nativeBindGroup) {
        super(resourceDomain);
        this.nativeBindGroup = nativeBindGroup;
    }

    WGPUBindGroup nativeBindGroup() {
        return nativeBindGroup;
    }

    @Override
    protected void releaseNative() {
        if (nativeBindGroup == null) {
            return;
        }
        WGPUCleanup cleanup = new WGPUCleanup();
        cleanup.run(() -> {
            if (nativeBindGroup.isValid()) {
                nativeBindGroup.release();
            }
        });
        cleanup.run(nativeBindGroup::dispose);
        cleanup.throwIfFailed();
    }
}
