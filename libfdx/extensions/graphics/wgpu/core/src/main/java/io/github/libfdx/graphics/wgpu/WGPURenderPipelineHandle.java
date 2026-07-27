package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

import java.util.ArrayList;

/**
 * Represents a WGPU render pipeline handle.
 *
 * @author xpenatan
 */
final class WGPURenderPipelineHandle extends WGPURecordedResource implements RenderPipeline {
    private final WGPURenderPipeline nativePipeline;
    private final WGPUPipelineLayout nativeLayout;
    private final WGPUBindGroupLayout textureBindGroupLayout;
    private final WGPUBindGroupLayout[] uniformBindGroupLayouts;
    private final int sampledTextureCount;
    private final int textureBindGroupIndex;
    private final int vertexBufferCount;
    private final ShaderRenderBindings resourceBindings;
    private final RenderTargetLayout targetLayout;
    private ArrayList<WGPUTextureBindGroupResource> textureBindGroups;

    WGPURenderPipelineHandle(WGPUResourceDomain resourceDomain, WGPURenderPipeline nativePipeline,
            WGPUPipelineLayout nativeLayout,
            WGPUBindGroupLayout textureBindGroupLayout,
            WGPUBindGroupLayout[] uniformBindGroupLayouts,
            int sampledTextureCount, int textureBindGroupIndex,
            int vertexBufferCount, ShaderRenderBindings resourceBindings,
            RenderTargetLayout targetLayout) {
        super(resourceDomain);
        this.nativePipeline = nativePipeline;
        this.nativeLayout = nativeLayout;
        this.textureBindGroupLayout = textureBindGroupLayout;
        this.uniformBindGroupLayouts = uniformBindGroupLayouts;
        this.sampledTextureCount = sampledTextureCount;
        this.textureBindGroupIndex = textureBindGroupIndex;
        this.vertexBufferCount = vertexBufferCount;
        this.resourceBindings = resourceBindings;
        this.targetLayout = targetLayout;
    }

    WGPURenderPipeline nativePipeline() {
        return nativePipeline;
    }

    WGPUBindGroupLayout textureBindGroupLayout() {
        return textureBindGroupLayout;
    }

    WGPUBindGroupLayout uniformBindGroupLayout(int index) {
        return uniformBindGroupLayouts[index];
    }

    int sampledTextureCount() {
        return sampledTextureCount;
    }

    int textureBindGroupIndex() {
        return textureBindGroupIndex;
    }

    int uniformBindGroupIndex(int index) {
        return resourceBindings.uniformSetIndex(index);
    }

    int vertexBufferCount() {
        return vertexBufferCount;
    }

    ShaderRenderBindings resourceBindings() {
        return resourceBindings;
    }

    @Override
    public RenderTargetLayout targetLayout() {
        return targetLayout;
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
        retire();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return isRetired();
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
        for (WGPUBindGroupLayout layout : uniformBindGroupLayouts) {
            if (layout != null) {
                cleanup.run(() -> resourceDomain().releaseUniformBindGroups(layout));
            }
        }
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
        if (textureBindGroupLayout != null) {
            cleanup.run(() -> {
                if (textureBindGroupLayout.isValid()) {
                    textureBindGroupLayout.release();
                }
            });
            cleanup.run(textureBindGroupLayout::dispose);
        }
        for (WGPUBindGroupLayout layout : uniformBindGroupLayouts) {
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
