package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for render pass implementations.
 *
 * @author xpenatan
 */
public interface RenderPass extends ProviderHandle {
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
     * Sets the uniform1f.
     *
     * @param name the name
     * @param value the value
     */
    default void setUniform1f(String name, float value) {
        throw new FdxException("Float uniforms are not supported by this render pass");
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
     * Sets the uniform matrix4.
     *
     * @param name the name
     * @param values the values
     */
    default void setUniformMatrix4(String name, float[] values) {
        throw new FdxException("Matrix uniforms are not supported by this render pass");
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
