package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Represents a GL render pass.
 *
 * @author xpenatan
 */
final class GLRenderPass implements RenderPass {
    private static final int MATRIX_FLOAT_COUNT = 16;
    private static final int MODEL_OFFSET = 0;
    private static final int VIEW_PROJECTION_OFFSET = 16;
    private static final int CAMERA_POSITION_OFFSET = 32;
    private static final int AMBIENT_COLOR_OFFSET = 36;
    private static final int LIGHT_DIRECTION_OFFSET = 40;
    private static final int LIGHT_COLOR_INTENSITY_OFFSET = 44;
    private static final int TEXTURE_FLAGS_OFFSET = 48;
    private static final int EMISSIVE_FLAGS_OFFSET = 52;
    private static final int PBR_UNIFORM_BINDING = 0;

    private final ProviderId providerId;
    private final GLApi gl;
    private GLRenderPipelineHandle pipeline;
    private GLBufferHandle[] vertexBuffers = new GLBufferHandle[2];
    private GLBufferHandle indexBuffer;
    private ByteBuffer pbrUniformBytes;
    private FloatBuffer pbrUniformFloats;
    private boolean pbrUniformDataDirty;
    private boolean ended;

    GLRenderPass(ProviderId providerId, GLApi gl) {
        this.providerId = providerId;
        this.gl = gl;
    }

    /**
     * Sets the pipeline.
     *
     * @param pipeline the pipeline
     */
    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        this.pipeline = pipeline.as();
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
        if (this.pipeline.pbrUniformBufferEnabled()) {
            resetPbrUniformData();
            gl.bindUniformBufferBase(PBR_UNIFORM_BINDING, this.pipeline.pbrUniformBuffer());
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
        if (buffer == null) {
            throw new FdxException("Vertex buffer cannot be null");
        }
        GLBufferHandle vertexBuffer = buffer.as();
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
        if (buffer == null) {
            throw new FdxException("Index buffer cannot be null");
        }
        indexBuffer = buffer.as();
        if (indexBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
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
        if (texture == null) {
            throw new FdxException("Texture cannot be null");
        }
        GLTextureHandle glTexture = texture.as();
        gl.activeTexture(slot);
        gl.bindTexture2D(glTexture.texture());
        if (pipeline != null) {
            setTextureUniform(slot);
        }
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
     * Sets the uniform1i.
     *
     * @param name the name
     * @param value the value
     */
    @Override
    public void setUniform1i(String name, int value) {
        setPbrUniform1i(name, value);
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform1i(location, value);
        }
    }

    /**
     * Sets the uniform1f.
     *
     * @param name the name
     * @param value the value
     */
    @Override
    public void setUniform1f(String name, float value) {
        setPbrUniform1f(name, value);
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform1f(location, value);
        }
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
        setPbrUniform3f(name, x, y, z);
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform3f(location, x, y, z);
        }
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
        setPbrUniform4f(name, x, y, z, w);
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniform4f(location, x, y, z, w);
        }
    }

    /**
     * Sets the uniform matrix4.
     *
     * @param name the name
     * @param values the values
     */
    @Override
    public void setUniformMatrix4(String name, float[] values) {
        if (values == null || values.length < MATRIX_FLOAT_COUNT) {
            throw new FdxException("Matrix uniform requires 16 float values");
        }
        setPbrUniformMatrix4(name, values);
        int location = uniformLocation(name);
        if (location >= 0) {
            gl.uniformMatrix4fv(location, false, values);
        }
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
        if (firstInstance != 0) {
            throw new FdxException("GL draw currently supports firstInstance=0 only");
        }
        applyPbrUniformBuffer();
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
        if (firstInstance != 0) {
            throw new FdxException("GL drawIndexed currently supports firstInstance=0 only");
        }
        applyPbrUniformBuffer();
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
        gl.useProgram(0);
        gl.bindArrayBuffer(0);
        gl.bindElementArrayBuffer(0);
        gl.bindUniformBuffer(0);
    }

    private void applyPbrUniformBuffer() {
        if (pipeline == null || !pipeline.pbrUniformBufferEnabled()) {
            return;
        }
        gl.bindUniformBufferBase(PBR_UNIFORM_BINDING, pipeline.pbrUniformBuffer());
        if (!pbrUniformDataDirty || pbrUniformBytes == null) {
            return;
        }
        pbrUniformBytes.position(0);
        pbrUniformBytes.limit(GLRenderPipelineHandle.PBR_UNIFORM_BYTE_COUNT);
        gl.bindUniformBuffer(pipeline.pbrUniformBuffer());
        gl.uniformBufferSubData(pbrUniformBytes);
        gl.bindUniformBuffer(0);
        pbrUniformDataDirty = false;
    }

    private void setPbrUniform1i(String name, int value) {
        if (!usesPbrUniformBuffer()) {
            return;
        }
        if ("u_hasBaseColorTexture".equals(name)) {
            setPbrUniformFloat(TEXTURE_FLAGS_OFFSET, value);
        } else if ("u_hasMetallicRoughnessTexture".equals(name)) {
            setPbrUniformFloat(TEXTURE_FLAGS_OFFSET + 1, value);
        } else if ("u_hasNormalTexture".equals(name)) {
            setPbrUniformFloat(TEXTURE_FLAGS_OFFSET + 2, value);
        } else if ("u_hasOcclusionTexture".equals(name)) {
            setPbrUniformFloat(TEXTURE_FLAGS_OFFSET + 3, value);
        } else if ("u_hasEmissiveTexture".equals(name)) {
            setPbrUniformFloat(EMISSIVE_FLAGS_OFFSET, value);
        }
    }

    private void setPbrUniform1f(String name, float value) {
        if (!usesPbrUniformBuffer()) {
            return;
        }
        if ("u_lightIntensity".equals(name)) {
            setPbrUniformFloat(LIGHT_COLOR_INTENSITY_OFFSET + 3, value);
        }
    }

    private void setPbrUniform3f(String name, float x, float y, float z) {
        if (!usesPbrUniformBuffer()) {
            return;
        }
        if ("u_cameraPosition".equals(name)) {
            setPbrUniform4f(CAMERA_POSITION_OFFSET, x, y, z, 1.0f);
        } else if ("u_ambientColor".equals(name)) {
            setPbrUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, 1.0f);
        } else if ("u_lightDirection".equals(name)) {
            setPbrUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, 0.0f);
        } else if ("u_lightColor".equals(name)) {
            setPbrUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z,
                    pbrUniformFloats.get(LIGHT_COLOR_INTENSITY_OFFSET + 3));
        }
    }

    private void setPbrUniform4f(String name, float x, float y, float z, float w) {
        if (!usesPbrUniformBuffer()) {
            return;
        }
        if ("u_cameraPosition".equals(name)) {
            setPbrUniform4f(CAMERA_POSITION_OFFSET, x, y, z, w);
        } else if ("u_ambientColor".equals(name)) {
            setPbrUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, w);
        } else if ("u_lightDirection".equals(name)) {
            setPbrUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, w);
        } else if ("u_lightColor".equals(name)) {
            setPbrUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z, w);
        }
    }

    private void setPbrUniformMatrix4(String name, float[] values) {
        if (!usesPbrUniformBuffer()) {
            return;
        }
        if ("u_model".equals(name)) {
            setPbrUniformMatrix(MODEL_OFFSET, values);
        } else if ("u_viewProjection".equals(name)) {
            setPbrUniformMatrix(VIEW_PROJECTION_OFFSET, values);
        }
    }

    private void setPbrUniformMatrix(int offset, float[] values) {
        ensurePbrUniformData();
        for (int i = 0; i < MATRIX_FLOAT_COUNT; i++) {
            pbrUniformFloats.put(offset + i, values[i]);
        }
        pbrUniformDataDirty = true;
    }

    private void setPbrUniform4f(int offset, float x, float y, float z, float w) {
        ensurePbrUniformData();
        pbrUniformFloats.put(offset, x);
        pbrUniformFloats.put(offset + 1, y);
        pbrUniformFloats.put(offset + 2, z);
        pbrUniformFloats.put(offset + 3, w);
        pbrUniformDataDirty = true;
    }

    private void setPbrUniformFloat(int offset, float value) {
        ensurePbrUniformData();
        pbrUniformFloats.put(offset, value);
        pbrUniformDataDirty = true;
    }

    private boolean usesPbrUniformBuffer() {
        return pipeline != null && pipeline.pbrUniformBufferEnabled();
    }

    private void resetPbrUniformData() {
        ensurePbrUniformData();
        for (int i = 0; i < GLRenderPipelineHandle.PBR_UNIFORM_BYTE_COUNT / 4; i++) {
            pbrUniformFloats.put(i, 0.0f);
        }
        pbrUniformFloats.put(MODEL_OFFSET, 1.0f);
        pbrUniformFloats.put(MODEL_OFFSET + 5, 1.0f);
        pbrUniformFloats.put(MODEL_OFFSET + 10, 1.0f);
        pbrUniformFloats.put(MODEL_OFFSET + 15, 1.0f);
        pbrUniformFloats.put(VIEW_PROJECTION_OFFSET, 1.0f);
        pbrUniformFloats.put(VIEW_PROJECTION_OFFSET + 5, 1.0f);
        pbrUniformFloats.put(VIEW_PROJECTION_OFFSET + 10, 1.0f);
        pbrUniformFloats.put(VIEW_PROJECTION_OFFSET + 15, 1.0f);
        pbrUniformDataDirty = true;
    }

    private void ensurePbrUniformData() {
        if (pbrUniformBytes != null) {
            return;
        }
        pbrUniformBytes = ByteBuffer.allocateDirect(GLRenderPipelineHandle.PBR_UNIFORM_BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        pbrUniformFloats = pbrUniformBytes.asFloatBuffer();
    }

    private void applyVertexLayouts() {
        if (pipeline == null) {
            return;
        }
        VertexLayout[] layouts = pipeline.vertexLayouts();
        for (int slot = 0; slot < layouts.length; slot++) {
            applyVertexLayout(slot);
        }
    }

    private void applyVertexLayout(int slot) {
        if (pipeline == null || slot >= vertexBuffers.length || vertexBuffers[slot] == null) {
            return;
        }
        VertexLayout[] layouts = pipeline.vertexLayouts();
        if (slot >= layouts.length) {
            return;
        }
        VertexLayout layout = layouts[slot];
        gl.bindArrayBuffer(vertexBuffers[slot].buffer());
        VertexAttribute[] attributes = layout.attributes();
        int divisor = layout.stepMode() == VertexStepMode.INSTANCE ? 1 : 0;
        for (int i = 0; i < attributes.length; i++) {
            VertexAttribute attribute = attributes[i];
            gl.enableVertexAttribArray(attribute.location());
            gl.vertexAttribPointer(attribute.location(), attribute.format(), layout.arrayStride(), attribute.offset());
            gl.vertexAttribDivisor(attribute.location(), divisor);
        }
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

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
        }
    }

    private int uniformLocation(String name) {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before uniforms");
        }
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
