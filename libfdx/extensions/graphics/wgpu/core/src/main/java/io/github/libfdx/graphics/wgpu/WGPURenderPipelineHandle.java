package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.RenderPipeline;

final class WGPURenderPipelineHandle implements RenderPipeline {
    private final WGPURenderPipeline nativePipeline;
    private final WGPUPipelineLayout nativeLayout;
    private final WGPUBindGroupLayout textureBindGroupLayout;
    private final WGPUBindGroupLayout uniformBindGroupLayout;
    private final int sampledTextureCount;
    private final int uniformBindGroupIndex;
    private boolean disposed;

    WGPURenderPipelineHandle(WGPURenderPipeline nativePipeline, WGPUPipelineLayout nativeLayout,
            WGPUBindGroupLayout textureBindGroupLayout, WGPUBindGroupLayout uniformBindGroupLayout,
            int sampledTextureCount, int uniformBindGroupIndex) {
        this.nativePipeline = nativePipeline;
        this.nativeLayout = nativeLayout;
        this.textureBindGroupLayout = textureBindGroupLayout;
        this.uniformBindGroupLayout = uniformBindGroupLayout;
        this.sampledTextureCount = sampledTextureCount;
        this.uniformBindGroupIndex = uniformBindGroupIndex;
    }

    WGPURenderPipeline nativePipeline() {
        return nativePipeline;
    }

    WGPUBindGroupLayout textureBindGroupLayout() {
        return textureBindGroupLayout;
    }

    WGPUBindGroupLayout uniformBindGroupLayout() {
        return uniformBindGroupLayout;
    }

    int sampledTextureCount() {
        return sampledTextureCount;
    }

    int uniformBindGroupIndex() {
        return uniformBindGroupIndex;
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
        nativePipeline.release();
        nativePipeline.dispose();
        nativeLayout.release();
        nativeLayout.dispose();
        if (textureBindGroupLayout != null && textureBindGroupLayout.isValid()) {
            textureBindGroupLayout.release();
            textureBindGroupLayout.dispose();
        }
        if (uniformBindGroupLayout != null && uniformBindGroupLayout.isValid()) {
            uniformBindGroupLayout.release();
            uniformBindGroupLayout.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
