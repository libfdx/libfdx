package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureView;

import java.util.ArrayList;

/**
 * Owns one native texture/view/sampler allocation used by a logical WGPU texture.
 */
final class WGPUTextureAllocation extends WGPURecordedResource {
    private final WGPUTexture nativeTexture;
    private final WGPUTextureView nativeView;
    private final WGPUTextureView nativeStorageView;
    private final WGPUSampler nativeSampler;
    private ArrayList<WGPUTextureBindGroupResource> textureBindGroups;

    WGPUTextureAllocation(WGPUResourceDomain resourceDomain,
            WGPUTexture nativeTexture, WGPUTextureView nativeView,
            WGPUTextureView nativeStorageView, WGPUSampler nativeSampler) {
        super(resourceDomain);
        this.nativeTexture = nativeTexture;
        this.nativeView = nativeView;
        this.nativeStorageView = nativeStorageView;
        this.nativeSampler = nativeSampler;
    }

    WGPUTexture nativeTexture() {
        return nativeTexture;
    }

    WGPUTextureView nativeView() {
        return nativeView;
    }

    WGPUTextureView nativeStorageView() {
        return nativeStorageView != null ? nativeStorageView : nativeView;
    }

    WGPUSampler nativeSampler() {
        return nativeSampler;
    }

    void addTextureBindGroup(WGPUTextureBindGroupResource bindGroup) {
        if (bindGroup == null) {
            return;
        }
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
        for (int i = 0; i < textureBindGroups.size(); i++) {
            if (textureBindGroups.get(i) == bindGroup) {
                textureBindGroups.remove(i);
                return;
            }
        }
    }

    @Override
    protected void onRetired() {
        while (textureBindGroups != null && !textureBindGroups.isEmpty()) {
            textureBindGroups.get(textureBindGroups.size() - 1).invalidate();
        }
    }

    @Override
    protected void releaseNative() {
        WGPUCleanup cleanup = new WGPUCleanup();
        if (nativeSampler != null) {
            cleanup.run(() -> {
                if (nativeSampler.isValid()) {
                    nativeSampler.release();
                }
            });
            cleanup.run(nativeSampler::dispose);
        }
        if (nativeView != null) {
            cleanup.run(() -> {
                if (nativeView.isValid()) {
                    nativeView.release();
                }
            });
            cleanup.run(nativeView::dispose);
        }
        if (nativeStorageView != null) {
            cleanup.run(() -> {
                if (nativeStorageView.isValid()) {
                    nativeStorageView.release();
                }
            });
            cleanup.run(nativeStorageView::dispose);
        }
        if (nativeTexture != null) {
            cleanup.run(() -> {
                if (nativeTexture.isValid()) {
                    nativeTexture.destroy();
                }
            });
            cleanup.run(() -> {
                if (nativeTexture.isValid()) {
                    nativeTexture.release();
                }
            });
            cleanup.run(nativeTexture::dispose);
        }
        cleanup.throwIfFailed();
    }
}
