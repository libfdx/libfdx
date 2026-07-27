package io.github.libfdx.graphics.gl;

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
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Represents a GL render pass.
 *
 * @author xpenatan
 */
final class GLRenderPass implements RenderPass {
    private static final int UNIFORM_BINDING = 0;

    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private GLTextureHandle renderTarget;
    private GLRenderPipelineHandle pipeline;
    private GLBufferHandle[] vertexBuffers = new GLBufferHandle[2];
    private boolean[] enabledVertexAttributes = new boolean[16];
    private GLTextureHandle[] textures = new GLTextureHandle[0];
    private GLBufferHandle indexBuffer;
    private ByteBuffer uniformBytes;
    private ShaderParameterBlock compatibilityUniformBlock;
    private boolean restoreDefaultFramebuffer;
    private RenderPassCompatibility compatibility;
    private int restoreWidth;
    private int restoreHeight;
    private boolean uniformDataDirty;
    private boolean hasUniformData;
    private boolean ended = true;

    GLRenderPass(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
    }

    void begin(GLTextureHandle renderTarget, boolean restoreDefaultFramebuffer, int restoreWidth,
            int restoreHeight, RenderPassCompatibility compatibility) {
        if (!ended) {
            throw new FdxException("Cannot reuse an active GL render pass");
        }
        this.renderTarget = renderTarget;
        this.restoreDefaultFramebuffer = restoreDefaultFramebuffer;
        this.restoreWidth = restoreWidth;
        this.restoreHeight = restoreHeight;
        this.compatibility = compatibility;
        pipeline = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
        indexBuffer = null;
        uniformDataDirty = false;
        hasUniformData = false;
        ended = false;
    }

    boolean isEnded() {
        return ended;
    }

    @Override
    public RenderPassCompatibility compatibility() {
        ensureOpen();
        return compatibility;
    }

    /**
     * Sets the pipeline.
     *
     * @param pipeline the pipeline
     */
    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        GLRenderPipelineHandle nextPipeline =
                GLResources.requirePipeline(pipeline, resourceDomain, "Render pipeline");
        if (!compatibility.isCompatible(nextPipeline.targetLayout())) {
            throw new FdxException("GL render pipeline target layout is incompatible with the active pass");
        }
        resetVertexAttributes();
        this.pipeline = nextPipeline;
        ensureTextureSlots(this.pipeline.sampledTextureCount());
        gl.useProgram(this.pipeline.program());
        gl.enableDepthTest(this.pipeline.depthTestEnabled());
        gl.depthMask(this.pipeline.depthWriteEnabled());
        if (this.pipeline.depthTestEnabled()) {
            gl.depthFuncLessEqual();
        }
        gl.enableAlphaBlending();
        if (this.pipeline.sampledTextureCount() > 0) {
            setTextureUniform(0);
        }
        compatibilityUniformBlock = null;
        ensureUniformCapacity();
        if (this.pipeline.uniformBufferEnabled()) {
            gl.bindUniformBufferBase(UNIFORM_BINDING, this.pipeline.uniformBuffer());
        }
        applyVertexLayouts();
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
        GLBufferHandle vertexBuffer = GLResources.requireBuffer(buffer, resourceDomain, "Vertex buffer");
        if (vertexBuffer.usage() != BufferUsage.VERTEX) {
            throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
        }
        ensureVertexBufferSlot(slot);
        vertexBuffers[slot] = vertexBuffer;
        gl.bindArrayBuffer(vertexBuffer.buffer());
        applyVertexLayout(slot);
    }

    /**
     * Sets the index buffer.
     *
     * @param buffer the buffer
     */
    @Override
    public void setIndexBuffer(Buffer buffer) {
        ensureOpen();
        GLBufferHandle nextIndexBuffer = GLResources.requireBuffer(buffer, resourceDomain, "Index buffer");
        if (nextIndexBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
        indexBuffer = nextIndexBuffer;
        gl.bindElementArrayBuffer(indexBuffer.buffer());
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
        if (pipeline == null || pipeline.sampledTextureCount() <= 0) {
            throw new FdxException("Current GL pipeline does not accept textures");
        }
        if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
            throw new FdxException("GL texture slot is outside the current pipeline texture range");
        }
        GLTextureHandle glTexture = GLResources.requireTexture(texture, resourceDomain, "Texture");
        if (!glTexture.usage().sampled()) {
            throw new FdxException("Texture was not created with sampled usage");
        }
        gl.activeTexture(slot);
        gl.bindTexture2D(glTexture.texture());
        textures[slot] = glTexture;
        setTextureUniform(slot);
    }

    @Override
    public void setTextureBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().textureSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Texture binding is not declared by the active GL pipeline: "
                    + group + ':' + binding);
        }
        setTexture(slot, texture);
    }

    @Override
    public void setTextureSamplerBinding(int group, int binding, Texture texture) {
        requirePipeline();
        int slot = pipeline.resourceBindings().samplerSlot(group, binding);
        if (slot < 0) {
            throw new FdxException("Sampler binding is not declared by the active GL pipeline: "
                    + group + ':' + binding);
        }
        setTexture(slot, texture);
    }

    @Override
    public void setSamplerBinding(int group, int binding, Sampler sampler) {
        throw new FdxException("Separate sampler objects are not supported by the GL provider");
    }

    @Override
    public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
        requirePipeline();
        pipeline.resourceBindings().requireParameterBlock(group, binding, block);
        ensureUniformCapacity();
        block.copyTo(uniformBytes, 0);
        hasUniformData = true;
        uniformDataDirty = true;
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
        gl.enableScissorTest(true);
        gl.scissor(x, y, width, height);
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
        gl.viewport(x, y, width, height);
    }

    /**
     * Sets the uniform1i.
     *
     * @param name the name
     * @param value the value
     */
    @Override
    public void setUniform1i(String name, int value) {
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform1i(location, value);
        }
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
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform1f(location, value);
        }
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
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform3f(location, x, y, z);
        }
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
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform4f(location, x, y, z, w);
        }
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
        if (values == null || values.length < 16) {
            throw new FdxException("Matrix uniform requires 16 float values");
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniformMatrix4fv(location, false, values);
        }
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
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before draw");
        }
        validateBoundResources(false);
        if (firstInstance != 0) {
            throw new FdxException("GL draw currently supports firstInstance=0 only");
        }
        applyUniformBuffer();
        if (instanceCount <= 1) {
            gl.drawArrays(pipeline.primitiveTopology(), firstVertex, vertexCount);
            return;
        }
        gl.drawArraysInstanced(pipeline.primitiveTopology(), firstVertex, vertexCount, instanceCount);
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
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before drawIndexed");
        }
        if (indexBuffer == null) {
            throw new FdxException("Index buffer must be set before drawIndexed");
        }
        validateBoundResources(true);
        if (firstInstance != 0) {
            throw new FdxException("GL drawIndexed currently supports firstInstance=0 only");
        }
        applyUniformBuffer();
        int offsetBytes = firstIndex * 2;
        if (instanceCount <= 1) {
            gl.drawElementsBaseVertex(pipeline.primitiveTopology(), indexCount, offsetBytes, baseVertex);
            return;
        }
        gl.drawElementsInstancedBaseVertex(pipeline.primitiveTopology(), indexCount, offsetBytes, instanceCount,
                baseVertex);
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
        gl.enableScissorTest(false);
        resetVertexAttributes();
        gl.useProgram(0);
        gl.bindArrayBuffer(0);
        gl.bindElementArrayBuffer(0);
        gl.bindUniformBuffer(0);
        gl.viewport(0, 0, restoreWidth, restoreHeight);
        if (restoreDefaultFramebuffer) {
            gl.bindFramebuffer(0);
        }
        pipeline = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
        indexBuffer = null;
        renderTarget = null;
        compatibility = null;
    }

    private void applyUniformBuffer() {
        if (pipeline == null || !pipeline.uniformBufferEnabled()) {
            return;
        }
        if (!hasUniformData) {
            throw new FdxException("GL uniform parameter block must be bound before drawing");
        }
        gl.bindUniformBufferBase(UNIFORM_BINDING, pipeline.uniformBuffer());
        if (!uniformDataDirty || uniformBytes == null) {
            return;
        }
        uniformBytes.position(0);
        uniformBytes.limit(pipeline.resourceBindings().uniformByteCount());
        gl.bindUniformBuffer(pipeline.uniformBuffer());
        gl.uniformBufferSubData(uniformBytes);
        gl.bindUniformBuffer(0);
        uniformDataDirty = false;
    }

    private ShaderParameterBlock compatibilityUniformBlock() {
        requirePipeline();
        if (!pipeline.resourceBindings().hasUniformBuffer()) {
            throw new FdxException("Active GL pipeline has no reflected uniform buffer");
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

    private void ensureUniformCapacity() {
        if (pipeline == null || !pipeline.resourceBindings().hasUniformBuffer()) {
            return;
        }
        int size = pipeline.resourceBindings().uniformByteCount();
        if (uniformBytes == null || uniformBytes.capacity() < size) {
            uniformBytes = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }
    }

    private void requirePipeline() {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before binding resources");
        }
        GLResources.requireUsable(pipeline, resourceDomain, "Render pipeline");
    }

    private void applyVertexLayouts() {
        if (pipeline == null) {
            return;
        }
        for (int slot = 0; slot < pipeline.vertexLayoutCount(); slot++) {
            applyVertexLayout(slot);
        }
    }

    private void applyVertexLayout(int slot) {
        if (pipeline == null || slot >= vertexBuffers.length || vertexBuffers[slot] == null) {
            return;
        }
        if (slot >= pipeline.vertexLayoutCount()) {
            return;
        }
        VertexLayout layout = pipeline.vertexLayout(slot);
        gl.bindArrayBuffer(vertexBuffers[slot].buffer());
        VertexAttribute[] attributes = layout.attributes();
        int divisor = layout.stepMode() == VertexStepMode.INSTANCE ? 1 : 0;
        for (int i = 0; i < attributes.length; i++) {
            VertexAttribute attribute = attributes[i];
            int location = attribute.location();
            ensureVertexAttributeSlot(location);
            if (!enabledVertexAttributes[location]) {
                gl.enableVertexAttribArray(location);
                enabledVertexAttributes[location] = true;
            }
            gl.vertexAttribPointer(location, attribute.format(), layout.arrayStride(), attribute.offset());
            gl.vertexAttribDivisor(location, divisor);
        }
    }

    private void resetVertexAttributes() {
        for (int location = 0; location < enabledVertexAttributes.length; location++) {
            if (enabledVertexAttributes[location]) {
                gl.disableVertexAttribArray(location);
                enabledVertexAttributes[location] = false;
            }
        }
    }

    private void ensureVertexAttributeSlot(int location) {
        if (location < enabledVertexAttributes.length) {
            return;
        }
        int next = enabledVertexAttributes.length;
        while (next <= location) {
            next *= 2;
        }
        enabledVertexAttributes = Arrays.copyOf(enabledVertexAttributes, next);
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

    private void ensureTextureSlots(int textureCount) {
        if (textures.length != textureCount) {
            textures = new GLTextureHandle[textureCount];
            return;
        }
        Arrays.fill(textures, null);
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
        }
        if (renderTarget != null && renderTarget.isDisposed()) {
            throw new FdxException("Render target texture has been disposed");
        }
    }

    private void validateBoundResources(boolean indexed) {
        GLResources.requireUsable(pipeline, resourceDomain, "Render pipeline");
        for (int slot = 0; slot < pipeline.vertexLayoutCount(); slot++) {
            if (slot >= vertexBuffers.length || vertexBuffers[slot] == null) {
                throw new FdxException("Vertex buffer slot " + slot + " must be set before draw");
            }
            if (vertexBuffers[slot].isDisposed()) {
                throw new FdxException("Vertex buffer slot " + slot + " has been disposed");
            }
        }
        if (indexed) {
            GLResources.requireUsable(indexBuffer, resourceDomain, "Index buffer");
        }
        for (int slot = 0; slot < pipeline.sampledTextureCount(); slot++) {
            GLTextureHandle texture = textures[slot];
            if (texture == null) {
                throw new FdxException("GL texture slot " + slot + " has not been set");
            }
            if (texture.isDisposed()) {
                throw new FdxException("GL texture slot " + slot + " has been disposed");
            }
        }
    }

    private int uniformLocation(String name) {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before uniforms");
        }
        GLResources.requireUsable(pipeline, resourceDomain, "Render pipeline");
        if (name == null || name.length() == 0) {
            throw new FdxException("Uniform name cannot be empty");
        }
        return gl.uniformLocation(pipeline.program(), name);
    }

    private void setTextureUniform(int slot) {
        int textureLocation = gl.uniformLocation(pipeline.program(), "u_texture");
        if (textureLocation >= 0) {
            gl.uniform1i(textureLocation, slot);
        }
        int tintTextureLocation = gl.uniformLocation(pipeline.program(), "f_u_texture_u_sampler");
        if (tintTextureLocation >= 0) {
            gl.uniform1i(tintTextureLocation, slot);
        }
        String[] names = pipeline.textureUniformNames(slot);
        for (String name : names) {
            int location = gl.uniformLocation(pipeline.program(), name);
            if (location >= 0) {
                gl.uniform1i(location, slot);
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
        return providerId;
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
}
