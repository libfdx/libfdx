package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroup;

/**
 * Owns a cached texture bind group until its context, pipeline, or any texture allocation retires.
 */
final class WGPUTextureBindGroupResource extends WGPURecordedResource {
    private final WGPUContext owner;
    private final WGPURenderPipelineHandle pipeline;
    private final WGPUTextureAllocation[] textureAllocations;
    private final WGPUSamplerAllocation[] samplerAllocations;
    private final WGPUTextureAllocation[] samplerTextureAllocations;
    private final WGPUBindGroup bindGroup;
    private boolean invalidated;

    WGPUTextureBindGroupResource(WGPUContext owner, WGPURenderPipelineHandle pipeline,
            WGPUTextureAllocation[] allocations, int count,
            WGPUSamplerAllocation[] samplers,
            WGPUTextureAllocation[] samplerTextures, int samplerCount,
            WGPUBindGroup bindGroup) {
        super(pipeline.resourceDomain());
        this.owner = owner;
        this.pipeline = pipeline;
        textureAllocations = new WGPUTextureAllocation[count];
        System.arraycopy(allocations, 0, textureAllocations, 0, count);
        samplerAllocations = new WGPUSamplerAllocation[samplerCount];
        samplerTextureAllocations = new WGPUTextureAllocation[samplerCount];
        System.arraycopy(samplers, 0, samplerAllocations, 0, samplerCount);
        System.arraycopy(samplerTextures, 0, samplerTextureAllocations, 0, samplerCount);
        this.bindGroup = bindGroup;
    }

    WGPUBindGroup bindGroup() {
        return bindGroup;
    }

    boolean matches(WGPURenderPipelineHandle expectedPipeline,
            WGPUTextureAllocation[] allocations, int count,
            WGPUSamplerAllocation[] samplers,
            WGPUTextureAllocation[] samplerTextures, int samplerCount) {
        if (invalidated || pipeline != expectedPipeline
                || textureAllocations.length != count
                || samplerAllocations.length != samplerCount) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (textureAllocations[i] != allocations[i]) {
                return false;
            }
        }
        for (int i = 0; i < samplerCount; i++) {
            if (samplerAllocations[i] != samplers[i]
                    || samplerTextureAllocations[i] != samplerTextures[i]) {
                return false;
            }
        }
        return true;
    }

    void attach() {
        pipeline.addTextureBindGroup(this);
        for (int i = 0; i < textureAllocations.length; i++) {
            textureAllocations[i].addTextureBindGroup(this);
        }
        for (int i = 0; i < samplerAllocations.length; i++) {
            if (samplerAllocations[i] != null) {
                samplerAllocations[i].addTextureBindGroup(this);
            }
            if (samplerTextureAllocations[i] != null) {
                samplerTextureAllocations[i].addTextureBindGroup(this);
            }
        }
    }

    void invalidate() {
        if (invalidated) {
            return;
        }
        invalidated = true;
        owner.removeTextureBindGroup(this);
        pipeline.removeTextureBindGroup(this);
        for (int i = 0; i < textureAllocations.length; i++) {
            textureAllocations[i].removeTextureBindGroup(this);
        }
        for (int i = 0; i < samplerAllocations.length; i++) {
            if (samplerAllocations[i] != null) {
                samplerAllocations[i].removeTextureBindGroup(this);
            }
            if (samplerTextureAllocations[i] != null) {
                samplerTextureAllocations[i].removeTextureBindGroup(this);
            }
        }
        retire();
    }

    @Override
    protected void releaseNative() {
        WGPUCleanup cleanup = new WGPUCleanup();
        cleanup.run(() -> {
            if (bindGroup.isValid()) {
                bindGroup.release();
            }
        });
        cleanup.run(bindGroup::dispose);
        cleanup.throwIfFailed();
    }
}
