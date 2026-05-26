package io.github.libfdx.graphics.gl;

import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;

import java.nio.ByteBuffer;

public interface GLApi {
    int createProgram();

    int createShader(GLShaderType type);

    void shaderSource(int shader, String source);

    void compileShader(int shader);

    boolean shaderCompileStatus(int shader);

    String shaderInfoLog(int shader);

    void deleteShader(int shader);

    void attachShader(int program, int shader);

    void linkProgram(int program);

    boolean programLinkStatus(int program);

    String programInfoLog(int program);

    void deleteProgram(int program);

    void useProgram(int program);

    int genVertexArray();

    void bindVertexArray(int vertexArray);

    void deleteVertexArray(int vertexArray);

    int genBuffer();

    void bindArrayBuffer(int buffer);

    default void bindElementArrayBuffer(int buffer) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    void bufferData(int size);

    default void elementBufferData(int size) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    void bufferSubData(ByteBuffer data);

    default void elementBufferSubData(ByteBuffer data) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    void deleteBuffer(int buffer);

    int genTexture();

    void bindTexture2D(int texture);

    void texImage2D(int width, int height, ByteBuffer data);

    void texSubImage2D(int width, int height, ByteBuffer data);

    void deleteTexture(int texture);

    default void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
    }

    void activeTexture(int slot);

    int uniformLocation(int program, String name);

    void uniform1i(int location, int value);

    default void uniform1f(int location, float value) {
    }

    default void uniform3f(int location, float x, float y, float z) {
    }

    default void uniform4f(int location, float x, float y, float z, float w) {
    }

    default void uniformMatrix4fv(int location, boolean transpose, float[] values) {
    }

    void enableAlphaBlending();

    default void enableDepthTest(boolean enabled) {
    }

    default void depthMask(boolean enabled) {
    }

    default void depthFuncLessEqual() {
    }

    void enableVertexAttribArray(int index);

    void vertexAttribPointer(int index, int size, int stride, int offset);

    default void vertexAttribDivisor(int index, int divisor) {
        if (divisor != 0) {
            throw new UnsupportedOperationException("Instanced vertex attributes are not supported");
        }
    }

    void viewport(int x, int y, int width, int height);

    void clearColor(float red, float green, float blue, float alpha);

    void clearColorBuffer();

    default void clearDepth(float depth) {
    }

    default void clearDepthBuffer() {
    }

    void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount);

    void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount);

    default ByteBuffer readPixelsRgba8(int width, int height) {
        throw new UnsupportedOperationException("Framebuffer readback is not supported");
    }

    default void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        throw new UnsupportedOperationException("Indexed draws are not supported");
    }

    default void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        throw new UnsupportedOperationException("Indexed draws are not supported");
    }
}
