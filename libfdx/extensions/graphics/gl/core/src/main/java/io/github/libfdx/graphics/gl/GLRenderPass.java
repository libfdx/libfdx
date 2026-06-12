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

import java.util.Arrays;

/**
 * Represents a GL render pass.
 *
 * @author xpenatan
 */
final class GLRenderPass implements RenderPass {
    private final ProviderId providerId;
    private final GLApi gl;
    private GLRenderPipelineHandle pipeline;
    private GLBufferHandle[] vertexBuffers = new GLBufferHandle[2];
    private GLBufferHandle indexBuffer;
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
            int textureLocation = gl.uniformLocation(this.pipeline.program(), "u_texture");
            if (textureLocation >= 0) {
                gl.uniform1i(textureLocation, 0);
            }
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
            int textureLocation = gl.uniformLocation(pipeline.program(), "u_texture");
            if (textureLocation >= 0) {
                gl.uniform1i(textureLocation, slot);
            }
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
        if (values == null || values.length < 16) {
            throw new FdxException("Matrix uniform requires 16 float values");
        }
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
