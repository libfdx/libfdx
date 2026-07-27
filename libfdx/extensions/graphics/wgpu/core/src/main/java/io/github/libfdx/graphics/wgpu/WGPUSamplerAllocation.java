package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUSampler;

import java.util.ArrayList;

/**
 * Owns one independently bindable native WGPU sampler.
 */
final class WGPUSamplerAllocation extends WGPURecordedResource {
    private final WGPUSampler nativeSampler;
    private ArrayList<WGPUTextureBindGroupResource> textureBindGroups;

    WGPUSamplerAllocation(WGPUResourceDomain resourceDomain, WGPUSampler nativeSampler) {
        super(resourceDomain);
        this.nativeSampler = nativeSampler;
    }

    WGPUSampler nativeSampler() {
        return nativeSampler;
    }

    void addTextureBindGroup(WGPUTextureBindGroupResource bindGroup) {
        if (textureBindGroups == null) {
            textureBindGroups = new ArrayList<WGPUTextureBindGroupResource>();
        }
        if (!textureBindGroups.contains(bindGroup)) {
            textureBindGroups.add(bindGroup);
        }
    }

    void removeTextureBindGroup(WGPUTextureBindGroupResource bindGroup) {
        if (textureBindGroups == null) {
            return;
        }
        textureBindGroups.remove(bindGroup);
    }

    @Override
    protected void onRetired() {
        while (textureBindGroups != null && !textureBindGroups.isEmpty()) {
            textureBindGroups.get(textureBindGroups.size() - 1).invalidate();
        }
    }

    @Override
    protected void releaseNative() {
        if (nativeSampler == null) {
            return;
        }
        WGPUCleanup cleanup = new WGPUCleanup();
        cleanup.run(() -> {
            if (nativeSampler.isValid()) {
                nativeSampler.release();
            }
        });
        cleanup.run(nativeSampler::dispose);
        cleanup.throwIfFailed();
    }
}
