package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderHandle;

public interface RenderPass extends ProviderHandle {
    void setPipeline(RenderPipeline pipeline);

    void setVertexBuffer(Buffer buffer);

    default void setVertexBuffer(int slot, Buffer buffer) {
        if (slot != 0) {
            throw new FdxException("Multiple vertex buffers are not supported by this render pass");
        }
        setVertexBuffer(buffer);
    }

    default void setIndexBuffer(Buffer buffer) {
        throw new FdxException("Index buffers are not supported by this render pass");
    }

    void setTexture(int slot, Texture texture);

    default void setUniform1i(String name, int value) {
        throw new FdxException("Integer uniforms are not supported by this render pass");
    }

    default void setUniform1f(String name, float value) {
        throw new FdxException("Float uniforms are not supported by this render pass");
    }

    default void setUniform3f(String name, float x, float y, float z) {
        throw new FdxException("Vector uniforms are not supported by this render pass");
    }

    default void setUniform4f(String name, float x, float y, float z, float w) {
        throw new FdxException("Vector uniforms are not supported by this render pass");
    }

    default void setUniformMatrix4(String name, float[] values) {
        throw new FdxException("Matrix uniforms are not supported by this render pass");
    }

    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    default void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        throw new FdxException("Indexed draws are not supported by this render pass");
    }

    void end();
}
