package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class D3D12CommandEncoder implements CommandEncoder {
    private final D3D12Context context;
    private final D3D12RenderPass renderPass;
    private boolean passActive;

    D3D12CommandEncoder(D3D12Context context) {
        this.context = context;
        renderPass = new D3D12RenderPass(context, this);
    }

    void beginFrame() {
        requireEnded();
        passActive = false;
    }

    void requireEnded() {
        if (passActive) {
            throw new FdxException("Direct3D 12 render pass must be ended before ending the frame");
        }
    }

    void ended() {
        passActive = false;
    }

    @Override
    public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPassDescriptor cannot be null");
        }
        context.requireFrame("begin a render pass");
        if (passActive) {
            throw new FdxException("Previous Direct3D 12 render pass must be ended first");
        }
        RenderPassCompatibility compatibility = descriptor.validate(context.device().capabilities());
        D3D12TextureView attachment = context.requireTextureView(descriptor.colorAttachment(), "Color attachment");
        LoadOp load = descriptor.colorLoadOp();
        StoreOp store = descriptor.colorStoreOp();
        D3D12Native.beginRenderPass(context.nativeHandle(), attachment.nativeHandle(), load.isClear(),
                load.red(), load.green(), load.blue(), load.alpha(), store.isStore(), descriptor.depthEnabled(),
                descriptor.depthClearEnabled(), descriptor.depthClearValue());
        passActive = true;
        renderPass.begin(attachment, RenderPassCompatibility.of(
                compatibility.targetLayout(), attachment.width(), attachment.height()));
        return renderPass;
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(context.nativeHandle());
    }
}

final class D3D12RenderPass implements RenderPass {
    private final D3D12Context context;
    private final D3D12CommandEncoder encoder;
    private ByteBuffer uniformBytes;
    private MemorySegment uniformMemory;
    private ShaderParameterBlock compatibilityUniformBlock;
    private D3D12TextureView colorAttachment;
    private D3D12Pipeline pipeline;
    private RenderPassCompatibility compatibility;
    private D3D12Buffer indexBuffer;
    private D3D12Buffer[] vertexBuffers = new D3D12Buffer[4];
    private D3D12Texture[] textures = new D3D12Texture[0];
    private boolean hasUniformData;
    private boolean ended = true;

    D3D12RenderPass(D3D12Context context, D3D12CommandEncoder encoder) {
        this.context = context;
        this.encoder = encoder;
    }

    void begin(D3D12TextureView colorAttachment, RenderPassCompatibility compatibility) {
        if (!ended) {
            throw new FdxException("Cannot reuse an active Direct3D 12 render pass");
        }
        this.colorAttachment = colorAttachment;
        this.compatibility = compatibility;
        pipeline = null;
        indexBuffer = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
        hasUniformData = false;
        compatibilityUniformBlock = null;
        ended = false;
    }

    @Override
    public RenderPassCompatibility compatibility() {
        ensureOpen();
        return compatibility;
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        this.pipeline = context.requirePipeline(pipeline, "Render pipeline");
        if (!compatibility.isCompatible(this.pipeline.targetLayout())) {
            this.pipeline = null;
            throw new FdxException(
                    "Direct3D 12 render pipeline target layout is incompatible with the active pass");
        }
        prepareTextureSlots(this.pipeline.sampledTextureCount());
        hasUniformData = false;
        compatibilityUniformBlock = null;
        ensureUniformCapacity();
        D3D12Native.setPipeline(context.nativeHandle(), this.pipeline.nativeHandle());
    }

    @Override
    public void setVertexBuffer(Buffer buffer) {
        setVertexBuffer(0, buffer);
    }

    @Override
    public void setVertexBuffer(int slot, Buffer buffer) {
        ensureOpen();
        if (slot < 0) {
            throw new FdxException("Vertex buffer slot cannot be negative");
        }
        D3D12Buffer target = context.requireBuffer(buffer, "Vertex buffer");
        if (target.usage() != BufferUsage.VERTEX) {
            throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
        }
        if (slot >= vertexBuffers.length) {
            vertexBuffers = Arrays.copyOf(vertexBuffers, Math.max(slot + 1, vertexBuffers.length * 2));
        }
        vertexBuffers[slot] = target;
        D3D12Native.setVertexBuffer(context.nativeHandle(), slot, target.nativeHandle());
    }

    @Override
    public void setIndexBuffer(Buffer buffer) {
        ensureOpen();
        indexBuffer = context.requireBuffer(buffer, "Index buffer");
        if (indexBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
        D3D12Native.setIndexBuffer(context.nativeHandle(), indexBuffer.nativeHandle());
    }

    @Override
    public void setTexture(int slot, Texture texture) {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before binding a texture");
        }
        if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
            throw new FdxException("Texture slot is not declared by the active Direct3D 12 pipeline: " + slot);
        }
        textures[slot] = context.requireTexture(texture, "Texture");
    }

    @Override
    public void setTextureBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().textureSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Texture binding is not declared by the active Direct3D 12 pipeline: "
                    + group + ':' + binding);
        }
        setTexture(slot, texture);
    }

    @Override
    public void setTextureSamplerBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().samplerSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Sampler binding is not declared by the active Direct3D 12 pipeline: "
                    + group + ':' + binding);
        }
        setTexture(slot, texture);
    }

    @Override
    public void setSamplerBinding(int group, int binding, Sampler sampler) {
        throw new FdxException("Separate sampler objects are not supported by the Direct3D 12 provider");
    }

    @Override
    public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
        requirePipeline();
        pipeline.resourceBindings().requireParameterBlock(group, binding, block);
        ensureUniformCapacity();
        block.copyTo(uniformBytes, 0);
        markUniformData();
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Scissor size must be greater than zero");
        }
        D3D12Native.setScissor(context.nativeHandle(), x, y, width, height);
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Viewport size must be greater than zero");
        }
        D3D12Native.setViewport(context.nativeHandle(), x, y, width, height);
    }

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

    @Override
    public void setUniform1f(String name, float value) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform1f(ShaderParameterHandle parameter, float value) {
        compatibilityUniformBlock().setFloat(parameter, value);
        snapshotCompatibilityBlock();
    }

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

    @Override
    public void setUniform4f(String name, float x, float y, float z, float w) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
        compatibilityUniformBlock().setFloat4(parameter, x, y, z, w);
        snapshotCompatibilityBlock();
    }

    @Override
    public void setUniformMatrix4(String name, float[] values) {
        throw namedUniformUnsupported(name);
    }

    @Override
    public void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
        compatibilityUniformBlock().setFloatMatrix(parameter, values, 0);
        snapshotCompatibilityBlock();
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(false);
        bindTextures();
        bindUniforms();
        D3D12Native.draw(context.nativeHandle(), vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(true);
        bindTextures();
        bindUniforms();
        D3D12Native.drawIndexed(context.nativeHandle(), indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    @Override
    public void end() {
        if (ended) {
            return;
        }
        D3D12Native.endRenderPass(context.nativeHandle());
        ended = true;
        encoder.ended();
        pipeline = null;
        indexBuffer = null;
        colorAttachment = null;
        compatibility = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
    }

    private void validateBoundResources(boolean indexed) {
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before drawing");
        }
        context.requirePipeline(pipeline, "Render pipeline");
        if (indexed && indexBuffer == null) {
            throw new FdxException("Index buffer must be set before drawIndexed");
        }
    }

    private void bindTextures() {
        if (pipeline.sampledTextureCount() == 0) {
            return;
        }
        for (int i = 0; i < pipeline.sampledTextureCount(); i++) {
            D3D12Texture texture = textures[i];
            if (texture == null || texture.isDisposed()) {
                throw new FdxException("Texture slot " + i + " must be set before drawing with Direct3D 12");
            }
            D3D12Native.setTexture(context.nativeHandle(), i, texture.nativeHandle());
        }
    }

    private void bindUniforms() {
        if (!pipeline.uniformBufferEnabled()) {
            return;
        }
        if (!hasUniformData) {
            throw new FdxException("Direct3D 12 uniforms must be set before drawing");
        }
        uniformBytes.position(0);
        int byteCount = pipeline.resourceBindings().uniformByteCount();
        uniformBytes.limit(byteCount);
        D3D12Native.bindUniforms(context.nativeHandle(), uniformMemory, byteCount);
    }

    private void prepareTextureSlots(int count) {
        if (textures.length < count) {
            textures = new D3D12Texture[count];
        }
        Arrays.fill(textures, null);
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
        }
        context.requireFrame("use a Direct3D 12 render pass");
        if (colorAttachment != null && colorAttachment.texture() != null && colorAttachment.texture().isDisposed()) {
            throw new FdxException("Render target texture has been disposed");
        }
    }

    private void requirePipeline() {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before binding resources");
        }
        context.requirePipeline(pipeline, "Render pipeline");
    }

    private void ensureUniformCapacity() {
        if (pipeline == null || !pipeline.resourceBindings().hasUniformBuffer()) {
            return;
        }
        int size = pipeline.resourceBindings().uniformByteCount();
        if (uniformBytes == null || uniformBytes.capacity() < size) {
            uniformBytes = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            uniformMemory = MemorySegment.ofBuffer(uniformBytes);
        }
    }

    private ShaderParameterBlock compatibilityUniformBlock() {
        requirePipeline();
        if (!pipeline.resourceBindings().hasUniformBuffer()) {
            throw new FdxException("Active Direct3D 12 pipeline has no reflected uniform buffer");
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
        return new FdxException("Direct3D 12 named uniform '" + name
                + "' is not portable; bind a reflected ShaderParameterBlock");
    }

    private void markUniformData() {
        hasUniformData = true;
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }
}
