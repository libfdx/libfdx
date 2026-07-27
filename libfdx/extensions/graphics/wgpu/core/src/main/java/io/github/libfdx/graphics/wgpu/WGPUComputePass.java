package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUComputePassEncoder;
import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValue;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;

import java.util.Arrays;

/**
 * Borrowed frame-owned WGPU compute command scope.
 */
final class WGPUComputePass implements ComputePass {
    private final WGPUContext context;
    private final WGPUComputePassEncoder nativePass = new WGPUComputePassEncoder();
    private WGPUComputePipelineHandle pipeline;
    private boolean[] boundGroups = new boolean[0];
    private boolean ended = true;

    WGPUComputePass(WGPUContext context) {
        this.context = context;
    }

    WGPUComputePassEncoder nativePass() {
        return nativePass;
    }

    void begin() {
        pipeline = null;
        Arrays.fill(boundGroups, false);
        ended = false;
    }

    boolean isEnded() {
        return ended;
    }

    void dispose() {
        WGPUCleanup cleanup = new WGPUCleanup();
        if (!ended) {
            cleanup.run(this::end);
        }
        cleanup.run(nativePass::dispose);
        cleanup.throwIfFailed();
    }

    @Override
    public void setPipeline(ComputePipeline value) {
        ensureOpen();
        WGPUComputePipelineHandle next = WGPUResources.requireComputePipeline(
                value, context.resourceDomain(), "Compute pipeline");
        context.markRecordedResource(next);
        pipeline = next;
        if (boundGroups.length < pipeline.groupCount()) {
            boundGroups = new boolean[pipeline.groupCount()];
        } else {
            Arrays.fill(boundGroups, false);
        }
        nativePass.setPipeline(pipeline.nativePipeline());
    }

    @Override
    public void setResourceSet(ShaderResourceSet set) {
        requirePipeline();
        if (set == null) {
            throw new FdxException("Shader resource set cannot be null");
        }
        if (!pipeline.resourceLayout().physicalHash().equals(set.layout().physicalHash())) {
            throw new FdxException("Shader resource set layout is incompatible with the compute pipeline");
        }
        int groupIndex = set.group();
        if (!pipeline.usesGroup(groupIndex)) {
            throw new FdxException("Compute pipeline does not declare resource group " + groupIndex);
        }

        WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
        for (int i = 0; i < set.valueCount(); i++) {
            ShaderResourceValue value = set.value(i);
            ShaderBinding binding = pipeline.resourceLayout().require(groupIndex, value.binding());
            entries.push_back(createEntry(binding, value));
        }

        WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx compute resource group " + groupIndex);
        descriptor.setLayout(pipeline.groupLayout(groupIndex));
        descriptor.setEntries(entries);
        WGPUBindGroup nativeBindGroup = new WGPUBindGroup();
        WGPUTransientBindGroup retained = null;
        try {
            context.nativeDevice().createBindGroup(descriptor, nativeBindGroup);
            if (!nativeBindGroup.isValid()) {
                throw new FdxException("Could not create WGPU compute bind group " + groupIndex);
            }
            retained = new WGPUTransientBindGroup(context.resourceDomain(), nativeBindGroup);
            context.markRecordedResource(retained);
            retained.retire();
            nativePass.setBindGroup(groupIndex, nativeBindGroup);
            boundGroups[groupIndex] = true;
        } catch (RuntimeException | Error failure) {
            if (retained == null) {
                rollbackBindGroup(nativeBindGroup, failure);
            }
            throw failure;
        }
    }

    private WGPUBindGroupEntry createEntry(ShaderBinding binding, ShaderResourceValue value) {
        WGPUBindGroupEntry entry = WGPUBindGroupEntry.obtain();
        entry.setNextInChain(WGPUChainedStruct.NULL);
        entry.setBinding(binding.binding());
        switch (value.kind()) {
            case PARAMETER_BLOCK -> bindParameterBlock(entry, binding, value.parameterBlock());
            case BUFFER -> bindBuffer(entry, binding, value);
            case TEXTURE -> bindTexture(entry, binding, value);
            case SAMPLER -> bindSampler(entry, binding, value);
            case TEXTURE_SAMPLER -> bindTextureSampler(entry, binding, value);
        }
        return entry;
    }

    private void bindParameterBlock(WGPUBindGroupEntry entry, ShaderBinding binding,
            ShaderParameterBlock block) {
        if (binding.resourceKind() != ShaderResourceKind.UNIFORM_BUFFER) {
            throw new FdxException("Parameter block requires a reflected uniform-buffer binding");
        }
        int size = align(block.byteSize(), 4);
        WGPUBufferDescriptor descriptor = WGPUBufferDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx compute parameter snapshot");
        descriptor.setSize(size);
        descriptor.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
        descriptor.setMappedAtCreation(false);
        WGPUBuffer nativeBuffer = new WGPUBuffer();
        WGPUBufferAllocation allocation = null;
        try {
            context.nativeDevice().createBuffer(descriptor, nativeBuffer);
            nativeBuffer.native_setAddress(nativeBuffer.native_getAddressLong());
            allocation = new WGPUBufferAllocation(context.resourceDomain(), nativeBuffer);
            context.nativeQueue().writeBuffer(nativeBuffer, 0, block.readOnlyData(), block.byteSize());
            context.markRecordedResource(allocation);
            allocation.retire();
            entry.setBuffer(nativeBuffer);
            entry.setOffset(0);
            entry.setSize(block.byteSize());
        } catch (RuntimeException | Error failure) {
            if (allocation == null) {
                rollbackBuffer(nativeBuffer, failure);
            }
            throw failure;
        }
    }

    private void bindBuffer(WGPUBindGroupEntry entry, ShaderBinding binding,
            ShaderResourceValue value) {
        WGPUBufferHandle buffer = WGPUResources.requireBuffer(
                value.buffer(), context.resourceDomain(), "Compute buffer binding");
        BufferUsage expected = binding.resourceKind() == ShaderResourceKind.UNIFORM_BUFFER
                ? BufferUsage.UNIFORM : BufferUsage.STORAGE;
        if (buffer.usage() != expected) {
            throw new FdxException("Compute buffer usage does not match reflected binding "
                    + binding.group() + ':' + binding.binding());
        }
        context.markRecordedResource(buffer.allocation());
        entry.setBuffer(buffer.nativeBuffer());
        entry.setOffset(value.offset());
        entry.setSize(value.size());
    }

    private void bindTexture(WGPUBindGroupEntry entry, ShaderBinding binding,
            ShaderResourceValue value) {
        WGPUTextureHandle texture = WGPUResources.requireTexture(
                value.texture(), context.resourceDomain(), "Compute texture binding");
        boolean storage = binding.resourceKind() == ShaderResourceKind.STORAGE_TEXTURE;
        if (storage ? !texture.usage().storage() : !texture.usage().sampled()) {
            throw new FdxException("Compute texture usage does not match reflected binding "
                    + binding.group() + ':' + binding.binding());
        }
        if (storage && WGPUTextureFormats.toCommon(binding.storageFormat()) != texture.format()) {
            throw new FdxException("Storage texture format does not match reflected binding "
                    + binding.group() + ':' + binding.binding());
        }
        boolean multisampled = binding.resourceKind() == ShaderResourceKind.MULTISAMPLED_TEXTURE
                || binding.resourceKind() == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE;
        if (multisampled != (texture.sampleCount() > 1)) {
            throw new FdxException("Texture sample count does not match reflected binding "
                    + binding.group() + ':' + binding.binding());
        }
        context.markRecordedResource(texture.allocation());
        entry.setTextureView(storage
                ? texture.nativeStorageView() : texture.nativeView());
    }

    private void bindSampler(WGPUBindGroupEntry entry, ShaderBinding binding,
            ShaderResourceValue value) {
        WGPUSamplerHandle sampler = WGPUResources.requireSampler(
                value.sampler(), context.resourceDomain(), "Compute sampler binding");
        requireSamplerKind(binding, sampler.kind());
        context.markRecordedResource(sampler.allocation());
        entry.setSampler(sampler.allocation().nativeSampler());
    }

    private void bindTextureSampler(WGPUBindGroupEntry entry, ShaderBinding binding,
            ShaderResourceValue value) {
        WGPUTextureHandle texture = WGPUResources.requireTexture(
                value.texture(), context.resourceDomain(), "Compute texture sampler binding");
        WGPUSampler sampler = texture.nativeSampler();
        if (sampler == null || !sampler.isValid()) {
            throw new FdxException("Texture does not own a bindable WGPU sampler");
        }
        requireSamplerKind(binding, ShaderSamplerKind.FILTERING);
        context.markRecordedResource(texture.allocation());
        entry.setSampler(sampler);
    }

    private static void requireSamplerKind(ShaderBinding binding, ShaderSamplerKind actual) {
        ShaderSamplerKind required = binding.samplerKind();
        if (required == ShaderSamplerKind.UNKNOWN_FILTERING) {
            required = ShaderSamplerKind.FILTERING;
        }
        if (required != actual) {
            throw new FdxException("Sampler kind does not match reflected binding "
                    + binding.group() + ':' + binding.binding());
        }
    }

    @Override
    public void dispatch(int workgroupCountX, int workgroupCountY, int workgroupCountZ) {
        requirePipeline();
        validateDispatch(workgroupCountX, workgroupCountY, workgroupCountZ,
                context.device().capabilities().limits());
        for (int group = 0; group < pipeline.groupCount(); group++) {
            if (pipeline.usesGroup(group) && !boundGroups[group]) {
                throw new FdxException("Compute resource group " + group
                        + " must be bound before dispatch");
            }
        }
        nativePass.setDispatchWorkgroups(workgroupCountX, workgroupCountY, workgroupCountZ);
    }

    @Override
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        nativePass.end();
        nativePass.release();
        pipeline = null;
        Arrays.fill(boundGroups, false);
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) nativePass;
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Compute pass has already ended");
        }
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot use a compute pass outside its active frame");
        }
    }

    private void requirePipeline() {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Compute pipeline must be set before binding or dispatch");
        }
        WGPUResources.requireComputePipeline(pipeline, context.resourceDomain(), "Compute pipeline");
    }

    private void rollbackBuffer(WGPUBuffer buffer, Throwable failure) {
        try {
            new WGPUBufferAllocation(context.resourceDomain(), buffer).retire();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void rollbackBindGroup(WGPUBindGroup bindGroup, Throwable failure) {
        try {
            if (bindGroup.isValid()) {
                bindGroup.release();
            }
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        try {
            bindGroup.dispose();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }
}
