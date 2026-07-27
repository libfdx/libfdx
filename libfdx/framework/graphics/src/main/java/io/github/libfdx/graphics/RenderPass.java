package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValue;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for render pass implementations.
 *
 * @author xpenatan
 */
public interface RenderPass extends ProviderHandle {
    /**
     * Returns the exact target compatibility of this active pass.
     *
     * @return render-pass compatibility
     */
    default RenderPassCompatibility compatibility() {
        throw new FdxException("This render pass does not expose exact compatibility metadata");
    }

    /**
     * Sets the pipeline.
     *
     * @param pipeline the pipeline
     */
    void setPipeline(RenderPipeline pipeline);

    /**
     * Sets the vertex buffer.
     *
     * @param buffer the buffer
     */
    void setVertexBuffer(Buffer buffer);

    /**
     * Sets the vertex buffer.
     *
     * @param slot the slot
     * @param buffer the buffer
     */
    default void setVertexBuffer(int slot, Buffer buffer) {
        if (slot != 0) {
            throw new FdxException("Multiple vertex buffers are not supported by this render pass");
        }
        setVertexBuffer(buffer);
    }

    /**
     * Sets the index buffer.
     *
     * @param buffer the buffer
     */
    default void setIndexBuffer(Buffer buffer) {
        throw new FdxException("Index buffers are not supported by this render pass");
    }

    /**
     * Sets the texture.
     *
     * @param slot the slot
     * @param texture the texture
     */
    void setTexture(int slot, Texture texture);

    /**
     * Binds one complete resource group.
     *
     * @param set immutable borrowed resource set
     */
    default void setResourceSet(ShaderResourceSet set) {
        if (set == null) {
            throw new FdxException("Shader resource set cannot be null");
        }
        for (int i = 0; i < set.valueCount(); i++) {
            ShaderResourceValue value = set.value(i);
            switch (value.kind()) {
                case PARAMETER_BLOCK -> setParameterBlock(set.group(), value.binding(),
                        value.parameterBlock());
                case BUFFER -> setBufferBinding(set.group(), value.binding(), value.buffer(),
                        value.offset(), value.size());
                case TEXTURE -> setTextureBinding(set.group(), value.binding(), value.texture());
                case SAMPLER -> setSamplerBinding(set.group(), value.binding(), value.sampler());
                case TEXTURE_SAMPLER -> setTextureSamplerBinding(
                        set.group(), value.binding(), value.texture());
            }
        }
    }

    /**
     * Binds a reflected CPU parameter block to a uniform-buffer slot.
     *
     * @param group bind group
     * @param binding binding index
     * @param block borrowed parameter block
     */
    default void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
        throw new FdxException("Shader parameter blocks are not supported by this render pass");
    }

    /**
     * Binds a buffer range to a reflected uniform/storage slot.
     *
     * @param group bind group
     * @param binding binding index
     * @param buffer borrowed buffer
     * @param offset byte offset
     * @param size byte size
     */
    default void setBufferBinding(int group, int binding, Buffer buffer, int offset, int size) {
        throw new FdxException("Shader buffer resources are not supported by this render pass");
    }

    /**
     * Binds a texture to a reflected resource slot.
     *
     * @param group bind group
     * @param binding binding index
     * @param texture borrowed texture
     */
    default void setTextureBinding(int group, int binding, Texture texture) {
        throw new FdxException("Explicit shader texture bindings are not supported by this render pass");
    }

    /**
     * Binds a separate sampler to a reflected resource slot.
     *
     * @param group bind group
     * @param binding binding index
     * @param sampler borrowed sampler
     */
    default void setSamplerBinding(int group, int binding, Sampler sampler) {
        throw new FdxException("Explicit shader samplers are not supported by this render pass");
    }

    /**
     * Binds the convenience sampler owned by a texture.
     *
     * @param group bind group
     * @param binding binding index
     * @param texture texture owning the sampler
     */
    default void setTextureSamplerBinding(int group, int binding, Texture texture) {
        throw new FdxException("Texture-owned shader samplers are not supported by this render pass");
    }

    /**
     * Sets the scissor.
     *
     * @param x the lower-left x coordinate in framebuffer pixels
     * @param y the lower-left y coordinate in framebuffer pixels
     * @param width the width in pixels
     * @param height the height in pixels
     */
    default void setScissor(int x, int y, int width, int height) {
        throw new FdxException("Scissor rectangles are not supported by this render pass");
    }

    /**
     * Sets the viewport.
     *
     * @param x the lower-left x coordinate in framebuffer pixels
     * @param y the lower-left y coordinate in framebuffer pixels
     * @param width the width in pixels
     * @param height the height in pixels
     */
    default void setViewport(int x, int y, int width, int height) {
        throw new FdxException("Viewports are not supported by this render pass");
    }

    /**
     * Sets the uniform1i.
     *
     * @param name the name
     * @param value the value
     */
    default void setUniform1i(String name, int value) {
        throw new FdxException("Integer uniforms are not supported by this render pass");
    }

    /**
     * Sets an integer-compatible value through a reflected parameter handle.
     *
     * @param parameter the reflected parameter handle
     * @param value the value
     */
    default void setUniform1i(ShaderParameterHandle parameter, int value) {
        throw new FdxException("Reflected integer uniforms are not supported by this render pass");
    }

    /**
     * Sets the uniform1f.
     *
     * @param name the name
     * @param value the value
     */
    default void setUniform1f(String name, float value) {
        throw new FdxException("Float uniforms are not supported by this render pass");
    }

    /**
     * Sets a scalar through a reflected parameter handle.
     *
     * @param parameter the reflected parameter handle
     * @param value the value
     */
    default void setUniform1f(ShaderParameterHandle parameter, float value) {
        throw new FdxException("Reflected float uniforms are not supported by this render pass");
    }

    /**
     * Sets the uniform3f.
     *
     * @param name the name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    default void setUniform3f(String name, float x, float y, float z) {
        throw new FdxException("Vector uniforms are not supported by this render pass");
    }

    /**
     * Sets a three-component value through a reflected parameter handle.
     *
     * @param parameter the reflected parameter handle
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    default void setUniform3f(ShaderParameterHandle parameter, float x, float y, float z) {
        throw new FdxException("Reflected vector uniforms are not supported by this render pass");
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
    default void setUniform4f(String name, float x, float y, float z, float w) {
        throw new FdxException("Vector uniforms are not supported by this render pass");
    }

    /**
     * Sets a four-component value through a reflected parameter handle.
     *
     * @param parameter the reflected parameter handle
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    default void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
        throw new FdxException("Reflected vector uniforms are not supported by this render pass");
    }

    /**
     * Sets the uniform matrix4.
     *
     * @param name the name
     * @param values the values
     */
    default void setUniformMatrix4(String name, float[] values) {
        throw new FdxException("Matrix uniforms are not supported by this render pass");
    }

    /**
     * Sets a matrix through a reflected parameter handle.
     *
     * @param parameter the reflected parameter handle
     * @param values the values
     */
    default void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
        throw new FdxException("Reflected matrix uniforms are not supported by this render pass");
    }

    /**
     * Draws the current content.
     *
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     * @param firstVertex the first vertex
     * @param firstInstance the first instance
     */
    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    /**
     * Draws indexed.
     *
     * @param indexCount the index count
     * @param instanceCount the instance count
     * @param firstIndex the first index
     * @param baseVertex the base vertex
     * @param firstInstance the first instance
     */
    default void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        throw new FdxException("Indexed draws are not supported by this render pass");
    }

    /**
     * Ends the operation.
     */
    void end();
}
