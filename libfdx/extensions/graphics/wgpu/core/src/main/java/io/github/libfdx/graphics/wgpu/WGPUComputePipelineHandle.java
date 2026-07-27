package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUComputePipeline;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;

/**
 * Persistent WGPU compute pipeline and its reflected resource layouts.
 */
final class WGPUComputePipelineHandle extends WGPURecordedResource implements ComputePipeline {
    private final WGPUComputePipeline nativePipeline;
    private final WGPUPipelineLayout nativeLayout;
    private final WGPUBindGroupLayout[] groupLayouts;
    private final boolean[] usedGroups;
    private final ShaderResourceLayout resourceLayout;

    WGPUComputePipelineHandle(WGPUResourceDomain resourceDomain,
            WGPUComputePipeline nativePipeline, WGPUPipelineLayout nativeLayout,
            WGPUBindGroupLayout[] groupLayouts, boolean[] usedGroups,
            ShaderResourceLayout resourceLayout) {
        super(resourceDomain);
        this.nativePipeline = nativePipeline;
        this.nativeLayout = nativeLayout;
        this.groupLayouts = groupLayouts != null
                ? groupLayouts.clone() : new WGPUBindGroupLayout[0];
        this.usedGroups = usedGroups != null ? usedGroups.clone() : new boolean[0];
        this.resourceLayout = resourceLayout;
    }

    WGPUComputePipeline nativePipeline() {
        return nativePipeline;
    }

    WGPUBindGroupLayout groupLayout(int group) {
        return group >= 0 && group < groupLayouts.length ? groupLayouts[group] : null;
    }

    int groupCount() {
        return groupLayouts.length;
    }

    boolean usesGroup(int group) {
        return group >= 0 && group < usedGroups.length && usedGroups[group];
    }

    ShaderResourceLayout resourceLayout() {
        return resourceLayout;
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
        retire();
    }

    @Override
    public boolean isDisposed() {
        return isRetired();
    }

    @Override
    protected void releaseNative() {
        WGPUCleanup cleanup = new WGPUCleanup();
        if (nativePipeline != null) {
            cleanup.run(() -> {
                if (nativePipeline.isValid()) {
                    nativePipeline.release();
                }
            });
            cleanup.run(nativePipeline::dispose);
        }
        if (nativeLayout != null) {
            cleanup.run(() -> {
                if (nativeLayout.isValid()) {
                    nativeLayout.release();
                }
            });
            cleanup.run(nativeLayout::dispose);
        }
        for (WGPUBindGroupLayout layout : groupLayouts) {
            if (layout != null) {
                cleanup.run(() -> {
                    if (layout.isValid()) {
                        layout.release();
                    }
                });
                cleanup.run(layout::dispose);
            }
        }
        cleanup.throwIfFailed();
    }
}
