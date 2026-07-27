package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Represents a WGPU render pass.
 *
 * @author xpenatan
 */
final class WGPURenderPass implements RenderPass {
    private final WGPUContext context;
    private final WGPURenderPassEncoder nativePass = new WGPURenderPassEncoder();
    private RenderPassCompatibility compatibility;
    private WGPUTextureHandle[] renderTargets = new WGPUTextureHandle[4];
    private int renderTargetCount;
    private ByteBuffer[] uniformBytes = new ByteBuffer[0];
    private ShaderParameterBlock compatibilityUniformBlock;
    private WGPURenderPipelineHandle pipeline;
    private WGPUBufferHandle[] vertexBuffers = new WGPUBufferHandle[2];
    private WGPUBufferHandle indexBuffer;
    private WGPUTextureHandle[] textures = new WGPUTextureHandle[0];
    private WGPUTextureAllocation[] textureAllocations = new WGPUTextureAllocation[0];
    private WGPUSamplerHandle[] samplers = new WGPUSamplerHandle[0];
    private WGPUSamplerAllocation[] samplerAllocations = new WGPUSamplerAllocation[0];
    private WGPUTextureHandle[] samplerTextures = new WGPUTextureHandle[0];
    private WGPUTextureAllocation[] samplerTextureAllocations =
            new WGPUTextureAllocation[0];
    private WGPUTextureBindGroupResource activeTextureBindGroup;
    private int[] uniformAllocationIndices = new int[0];
    private boolean textureBindGroupDirty;
    private boolean[] uniformDataDirty = new boolean[0];
    private boolean[] hasUniformData = new boolean[0];
    private boolean ended = true;

    WGPURenderPass(WGPUContext context) {
        this.context = context;
    }

    WGPURenderPassEncoder nativePass() {
        return nativePass;
    }

    void begin(RenderPassCompatibility compatibility,
            WGPUTextureHandle[] targets, int targetCount) {
        this.compatibility = compatibility;
        if (renderTargets.length < targetCount) {
            renderTargets = new WGPUTextureHandle[targetCount];
        }
        Arrays.fill(renderTargets, null);
        System.arraycopy(targets, 0, renderTargets, 0, targetCount);
        renderTargetCount = targetCount;
        pipeline = null;
        Arrays.fill(vertexBuffers, null);
        indexBuffer = null;
        Arrays.fill(textures, null);
        Arrays.fill(textureAllocations, null);
        Arrays.fill(samplers, null);
        Arrays.fill(samplerAllocations, null);
        Arrays.fill(samplerTextures, null);
        Arrays.fill(samplerTextureAllocations, null);
        activeTextureBindGroup = null;
        Arrays.fill(uniformAllocationIndices, -1);
        textureBindGroupDirty = false;
        Arrays.fill(uniformDataDirty, false);
        Arrays.fill(hasUniformData, false);
        compatibilityUniformBlock = null;
        ended = false;
    }

    @Override
    public RenderPassCompatibility compatibility() {
        ensureOpen();
        return compatibility;
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

    /**
     * Sets the pipeline.
     *
     * @param pipeline the pipeline
     */
    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        WGPURenderPipelineHandle nextPipeline = WGPUResources.requirePipeline(pipeline, context.resourceDomain(),
                "Render pipeline");
        if (!compatibility.isCompatible(nextPipeline.targetLayout())) {
            throw new FdxException("WGPU render pipeline target layout is incompatible with the active pass");
        }
        context.markRecordedResource(nextPipeline);
        this.pipeline = nextPipeline;
        ensureTextureSlots(this.pipeline.sampledTextureCount());
        ensureSamplerSlots(this.pipeline.resourceBindings().samplerCount());
        releaseActiveTextureBindGroup();
        ensureUniformSlots(this.pipeline.resourceBindings().uniformBufferCount());
        Arrays.fill(uniformAllocationIndices, -1);
        Arrays.fill(uniformDataDirty, false);
        Arrays.fill(hasUniformData, false);
        compatibilityUniformBlock = null;
        textureBindGroupDirty = this.pipeline.textureBindGroupLayout() != null;
        nativePass.setPipeline(this.pipeline.nativePipeline());
    }

    /**
     * Sets the vertex buffer.
     *
     * @param buffer the buffer
     */
    @Override
    public void setVertexBuffer(Buffer buffer) {
        setVertexBuffer(0, buffer);
    }

    /**
     * Sets the vertex buffer.
     *
     * @param slot the slot
     * @param buffer the buffer
     */
    @Override
    public void setVertexBuffer(int slot, Buffer buffer) {
        ensureOpen();
        if (slot < 0) {
            throw new FdxException("Vertex buffer slot cannot be negative");
        }
        WGPUBufferHandle wgpuBuffer = WGPUResources.requireBuffer(buffer, context.resourceDomain(), "Vertex buffer");
        if (wgpuBuffer.usage() != BufferUsage.VERTEX) {
            throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
        }
        ensureVertexBufferSlot(slot);
        vertexBuffers[slot] = wgpuBuffer;
        context.markRecordedResource(wgpuBuffer.allocation());
        nativePass.setVertexBuffer(slot, wgpuBuffer.nativeBuffer(), 0, wgpuBuffer.size());
    }

    /**
     * Sets the index buffer.
     *
     * @param buffer the buffer
     */
    @Override
    public void setIndexBuffer(Buffer buffer) {
        ensureOpen();
        WGPUBufferHandle wgpuBuffer = WGPUResources.requireBuffer(buffer, context.resourceDomain(), "Index buffer");
        if (wgpuBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
        indexBuffer = wgpuBuffer;
        context.markRecordedResource(wgpuBuffer.allocation());
        nativePass.setIndexBuffer(wgpuBuffer.nativeBuffer(), WGPUIndexFormat.Uint16, 0, wgpuBuffer.size());
    }

    /**
     * Sets the texture.
     *
     * @param slot the slot
     * @param texture the texture
     */
    @Override
    public void setTexture(int slot, Texture texture) {
        ensureOpen();
        if (pipeline == null || pipeline.textureBindGroupLayout() == null) {
            throw new FdxException("Current WGPU pipeline does not accept textures");
        }
        if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
            throw new FdxException("WGPU texture slot is outside the current pipeline texture range");
        }
        WGPUTextureHandle wgpuTexture = WGPUResources.requireTexture(texture, context.resourceDomain(), "Texture");
        if (!wgpuTexture.usage().sampled()) {
            throw new FdxException("Texture was not created with sampled usage");
        }
        if (textures[slot] != wgpuTexture) {
            textures[slot] = wgpuTexture;
            textureBindGroupDirty = true;
        }
    }

    @Override
    public void setTextureBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().textureSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Texture binding is not declared by the active WGPU pipeline: "
                    + group + ':' + binding);
        }
        setTexture(slot, texture);
    }

    @Override
    public void setTextureSamplerBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().samplerSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Sampler binding is not declared by the active WGPU pipeline: "
                    + group + ':' + binding);
        }
        WGPUTextureHandle wgpuTexture = WGPUResources.requireTexture(
                texture, context.resourceDomain(), "Texture sampler source");
        if (!wgpuTexture.usage().sampled()
                || wgpuTexture.nativeSampler() == null
                || !wgpuTexture.nativeSampler().isValid()) {
            throw new FdxException("Texture does not own a bindable WGPU sampler");
        }
        if (samplerTextures[slot] != wgpuTexture || samplers[slot] != null) {
            samplerTextures[slot] = wgpuTexture;
            samplers[slot] = null;
            textureBindGroupDirty = true;
        }
    }

    @Override
    public void setSamplerBinding(int group, int binding, Sampler sampler) {
        requirePipeline();
        int slot = pipeline.resourceBindings().samplerSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Sampler binding is not declared by the active WGPU pipeline: "
                    + group + ':' + binding);
        }
        WGPUSamplerHandle wgpuSampler = WGPUResources.requireSampler(
                sampler, context.resourceDomain(), "Sampler binding");
        io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind required =
                pipeline.resourceBindings().sampler(slot).samplerKind();
        if (required == io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind.UNKNOWN_FILTERING) {
            required = io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind.FILTERING;
        }
        if (required != wgpuSampler.kind()) {
            throw new FdxException("Sampler kind does not match reflected WGPU binding "
                    + group + ':' + binding);
        }
        if (samplers[slot] != wgpuSampler || samplerTextures[slot] != null) {
            samplers[slot] = wgpuSampler;
            samplerTextures[slot] = null;
            textureBindGroupDirty = true;
        }
    }

    @Override
    public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
        requirePipeline();
        pipeline.resourceBindings().requireParameterBlock(group, binding, block);
        int index = pipeline.resourceBindings()
                .uniformBufferIndex(group, binding);
        ensureUniformCapacity(index);
        block.copyTo(uniformBytes[index], 0);
        markUniformDirty(index);
        uniformAllocationIndices[index] = -1;
    }

    /**
     * Sets the scissor.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void setScissor(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Scissor size must be greater than zero");
        }
        nativePass.setScissorRect(x, compatibility.height() - y - height, width, height);
    }

    /**
     * Sets the viewport.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void setViewport(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Viewport size must be greater than zero");
        }
        nativePass.setViewport(x, compatibility.height() - y - height, width, height, 0.0f, 1.0f);
    }

    /**
     * Sets the uniform1i.
     *
     * @param name the name
     * @param value the value
     */
    @Override
    public void setUniform1i(String name, int value) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform1i(ShaderParameterHandle parameter, int value) {
        ShaderParameterBlock block = compatibilityUniformBlock();
        switch (parameter.valueType().scalarType()) {
            case F32 -> block.setFloat(parameter, value);
            case I32 -> block.setInt(parameter, value);
            case U32 -> block.setUnsignedInt(parameter, value);
            case BOOL -> block.setBoolean(parameter, value != 0);
            default -> throw new FdxException("Uniform handle is not integer-compatible: "
                    + parameter.path());
        }
        snapshotCompatibilityBlock();
    }

    /**
     * Sets the uniform1f.
     *
     * @param name the name
     * @param value the value
     */
    @Override
    public void setUniform1f(String name, float value) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform1f(ShaderParameterHandle parameter, float value) {
        compatibilityUniformBlock().setFloat(parameter, value);
        snapshotCompatibilityBlock();
    }

    /**
     * Sets the uniform3f.
     *
     * @param name the name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    @Override
    public void setUniform3f(String name, float x, float y, float z) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform3f(ShaderParameterHandle parameter, float x, float y, float z) {
        ShaderParameterBlock block = compatibilityUniformBlock();
        block.setFloat(parameter.component(0), x);
        block.setFloat(parameter.component(1), y);
        block.setFloat(parameter.component(2), z);
        snapshotCompatibilityBlock();
    }

    /**
     * Sets the uniform4f.
     *
     * @param name the name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    @Override
    public void setUniform4f(String name, float x, float y, float z, float w) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
        compatibilityUniformBlock().setFloat4(parameter, x, y, z, w);
        snapshotCompatibilityBlock();
    }

    /**
     * Sets the uniform matrix4.
     *
     * @param name the name
     * @param values the values
     */
    @Override
    public void setUniformMatrix4(String name, float[] values) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
        compatibilityUniformBlock().setFloatMatrix(parameter, values, 0);
        snapshotCompatibilityBlock();
    }

    /**
     * Draws the current content.
     *
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     * @param firstVertex the first vertex
     * @param firstInstance the first instance
     */
    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(false);
        applyBindGroups();
        nativePass.draw(vertexCount, instanceCount, firstVertex, firstInstance);
    }

    /**
     * Draws indexed.
     *
     * @param indexCount the index count
     * @param instanceCount the instance count
     * @param firstIndex the first index
     * @param baseVertex the base vertex
     * @param firstInstance the first instance
     */
    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(true);
        applyBindGroups();
        nativePass.drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    /**
     * Ends the operation.
     */
    @Override
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        releaseActiveTextureBindGroup();
        nativePass.end();
        nativePass.release();
        pipeline = null;
        Arrays.fill(vertexBuffers, null);
        indexBuffer = null;
        Arrays.fill(textures, null);
        Arrays.fill(textureAllocations, null);
        Arrays.fill(samplers, null);
        Arrays.fill(samplerAllocations, null);
        Arrays.fill(samplerTextures, null);
        Arrays.fill(samplerTextureAllocations, null);
        Arrays.fill(renderTargets, null);
        renderTargetCount = 0;
        compatibility = null;
    }

    private void applyBindGroups() {
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before draw");
        }
        applyTextureBindGroup();
        applyUniformBindGroup();
    }

    private void applyTextureBindGroup() {
        int textureCount = pipeline.sampledTextureCount();
        int samplerCount = pipeline.resourceBindings().samplerCount();
        if (textureCount <= 0 && samplerCount <= 0) {
            return;
        }
        for (int i = 0; i < textureCount; i++) {
            WGPUTextureHandle texture = textures[i];
            if (texture == null) {
                throw new FdxException("WGPU texture slot " + i + " has not been set");
            }
            if (texture.isDisposed()) {
                throw new FdxException("WGPU texture slot " + i + " has been disposed");
            }
            WGPUTextureAllocation allocation = texture.allocation();
            if (textureAllocations[i] != allocation) {
                textureBindGroupDirty = true;
            }
            textureAllocations[i] = allocation;
            context.markRecordedResource(allocation);
        }
        for (int i = 0; i < samplerCount; i++) {
            WGPUSamplerHandle sampler = samplers[i];
            WGPUTextureHandle samplerTexture = samplerTextures[i];
            if (sampler == null && samplerTexture == null && i < textureCount) {
                samplerTexture = textures[i];
            }
            if (sampler == null && samplerTexture == null) {
                throw new FdxException("WGPU sampler slot " + i + " has not been set");
            }
            if (sampler != null) {
                if (sampler.isDisposed()) {
                    throw new FdxException("WGPU sampler slot " + i + " has been disposed");
                }
                WGPUSamplerAllocation allocation = sampler.allocation();
                if (samplerAllocations[i] != allocation
                        || samplerTextureAllocations[i] != null) {
                    textureBindGroupDirty = true;
                }
                samplerAllocations[i] = allocation;
                samplerTextureAllocations[i] = null;
                context.markRecordedResource(allocation);
            } else {
                if (samplerTexture.isDisposed()
                        || samplerTexture.nativeSampler() == null
                        || !samplerTexture.nativeSampler().isValid()) {
                    throw new FdxException("WGPU texture sampler slot " + i
                            + " is not usable");
                }
                WGPUTextureAllocation allocation = samplerTexture.allocation();
                if (samplerTextureAllocations[i] != allocation
                        || samplerAllocations[i] != null) {
                    textureBindGroupDirty = true;
                }
                samplerTextureAllocations[i] = allocation;
                samplerAllocations[i] = null;
                context.markRecordedResource(allocation);
            }
        }
        if (activeTextureBindGroup == null || textureBindGroupDirty) {
            releaseActiveTextureBindGroup();
            activeTextureBindGroup = context.textureBindGroup(pipeline,
                    textureAllocations, textureCount, samplerAllocations,
                    samplerTextureAllocations, samplerCount);
            textureBindGroupDirty = false;
        }
        nativePass.setBindGroup(pipeline.textureBindGroupIndex(), activeTextureBindGroup.bindGroup());
    }

    private void applyUniformBindGroup() {
        int count = pipeline.resourceBindings().uniformBufferCount();
        for (int i = 0; i < count; i++) {
            if (!hasUniformData[i]) {
                throw new FdxException("WGPU uniform parameter block "
                        + pipeline.resourceBindings().uniformBuffer(i).group()
                        + ':' + pipeline.resourceBindings().uniformBuffer(i).binding()
                        + " must be bound before drawing");
            }
            uniformAllocationIndices[i] = context.bindUniforms(nativePass,
                    pipeline, i, uniformBytes[i],
                    uniformDataDirty[i] || uniformAllocationIndices[i] < 0
                            ? -1 : uniformAllocationIndices[i]);
            uniformDataDirty[i] = false;
        }
    }

    private void markUniformDirty(int index) {
        hasUniformData[index] = true;
        uniformDataDirty[index] = true;
    }

    private void ensureTextureSlots(int textureCount) {
        if (textureCount <= 0) {
            Arrays.fill(textures, null);
            Arrays.fill(textureAllocations, null);
            return;
        }
        if (textures.length != textureCount) {
            textures = new WGPUTextureHandle[textureCount];
            textureAllocations = new WGPUTextureAllocation[textureCount];
            return;
        }
        Arrays.fill(textures, null);
        Arrays.fill(textureAllocations, null);
    }

    private void ensureSamplerSlots(int samplerCount) {
        if (samplerCount <= 0) {
            samplers = new WGPUSamplerHandle[0];
            samplerAllocations = new WGPUSamplerAllocation[0];
            samplerTextures = new WGPUTextureHandle[0];
            samplerTextureAllocations = new WGPUTextureAllocation[0];
            return;
        }
        if (samplers.length != samplerCount) {
            samplers = new WGPUSamplerHandle[samplerCount];
            samplerAllocations = new WGPUSamplerAllocation[samplerCount];
            samplerTextures = new WGPUTextureHandle[samplerCount];
            samplerTextureAllocations = new WGPUTextureAllocation[samplerCount];
            return;
        }
        Arrays.fill(samplers, null);
        Arrays.fill(samplerAllocations, null);
        Arrays.fill(samplerTextures, null);
        Arrays.fill(samplerTextureAllocations, null);
    }

    private void ensureVertexBufferSlot(int slot) {
        if (slot < vertexBuffers.length) {
            return;
        }
        int next = vertexBuffers.length;
        while (next <= slot) {
            next *= 2;
        }
        vertexBuffers = Arrays.copyOf(vertexBuffers, next);
    }

    private void validateBoundResources(boolean indexed) {
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before draw");
        }
        WGPUResources.requireUsable(pipeline, context.resourceDomain(), "Render pipeline");
        for (int slot = 0; slot < pipeline.vertexBufferCount(); slot++) {
            if (slot >= vertexBuffers.length || vertexBuffers[slot] == null) {
                throw new FdxException("Vertex buffer slot " + slot + " must be set before draw");
            }
            if (vertexBuffers[slot].isDisposed()) {
                throw new FdxException("Vertex buffer slot " + slot + " has been disposed");
            }
        }
        if (indexed) {
            if (indexBuffer == null) {
                throw new FdxException("Index buffer must be set before drawIndexed");
            }
            WGPUResources.requireUsable(indexBuffer, context.resourceDomain(), "Index buffer");
        }
    }

    private void releaseActiveTextureBindGroup() {
        activeTextureBindGroup = null;
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
        }
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot use a render pass outside its active frame");
        }
        for (int i = 0; i < renderTargetCount; i++) {
            if (renderTargets[i].isDisposed()) {
                throw new FdxException("Render target texture has been disposed");
            }
        }
    }

    private void requirePipeline() {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before binding resources");
        }
        WGPUResources.requireUsable(pipeline, context.resourceDomain(), "Render pipeline");
    }

    private void ensureUniformSlots(int count) {
        if (uniformBytes.length == count) {
            return;
        }
        uniformBytes = new ByteBuffer[count];
        uniformAllocationIndices = new int[count];
        uniformDataDirty = new boolean[count];
        hasUniformData = new boolean[count];
        Arrays.fill(uniformAllocationIndices, -1);
    }

    private void ensureUniformCapacity(int index) {
        int size = pipeline.resourceBindings().uniformByteCount(index);
        if (uniformBytes[index] == null
                || uniformBytes[index].capacity() < size) {
            uniformBytes[index] = ByteBuffer.allocateDirect(size)
                    .order(ByteOrder.nativeOrder());
        }
    }

    private ShaderParameterBlock compatibilityUniformBlock() {
        requirePipeline();
        if (!pipeline.resourceBindings().hasUniformBuffer()) {
            throw new FdxException("Active WGPU pipeline has no reflected uniform buffer");
        }
        if (compatibilityUniformBlock == null) {
            compatibilityUniformBlock = ShaderParameterBlock.allocate(
                    pipeline.resourceBindings().uniformBuffer().bufferLayout());
        }
        return compatibilityUniformBlock;
    }

    private void snapshotCompatibilityBlock() {
        setParameterBlock(pipeline.resourceBindings().uniformGroup(),
                pipeline.resourceBindings().uniformBinding(), compatibilityUniformBlock);
    }

    private FdxException namedUniformUnsupported(String name) {
        ensureOpen();
        return new FdxException("WGPU named uniform '" + name
                + "' is not portable; bind a reflected ShaderParameterBlock");
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
        return (T) nativePass;
    }
}
