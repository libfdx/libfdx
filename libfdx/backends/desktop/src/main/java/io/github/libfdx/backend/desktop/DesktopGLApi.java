package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

final class DesktopGLApi implements GLApi {
    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }

    @Override
    public int createShader(GLShaderType type) {
        if (type == GLShaderType.VERTEX) {
            return GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        }
        throw new FdxException("Unsupported GL shader type: " + type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    @Override
    public boolean shaderCompileStatus(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE;
    }

    @Override
    public String shaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    @Override
    public boolean programLinkStatus(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE;
    }

    @Override
    public String programInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    @Override
    public int genVertexArray() {
        return GL30.glGenVertexArrays();
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        GL30.glBindVertexArray(vertexArray);
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        GL30.glDeleteVertexArrays(vertexArray);
    }

    @Override
    public int genBuffer() {
        return GL15.glGenBuffers();
    }

    @Override
    public void bindArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
    }

    @Override
    public void bindElementArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    @Override
    public void bufferData(int size) {
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    }

    @Override
    public void elementBufferData(int size) {
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, size, GL15.GL_STATIC_DRAW);
    }

    @Override
    public void bufferSubData(ByteBuffer data) {
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
    }

    @Override
    public void elementBufferSubData(ByteBuffer data) {
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, data);
    }

    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    @Override
    public int genTexture() {
        return GL11.glGenTextures();
    }

    @Override
    public void bindTexture2D(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public void texImage2D(int width, int height, ByteBuffer data) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, toNative(wrapS));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, toNative(wrapT));
    }

    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    @Override
    public void activeTexture(int slot) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot);
    }

    @Override
    public int uniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        GL20.glUniform1f(location, value);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        GL20.glUniform3f(location, x, y, z);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL20.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        GL20.glUniformMatrix4fv(location, transpose, values);
    }

    @Override
    public void enableAlphaBlending() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    @Override
    public void depthMask(boolean enabled) {
        GL11.glDepthMask(enabled);
    }

    @Override
    public void depthFuncLessEqual() {
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void clearDepth(float depth) {
        GL11.glClearDepth(depth);
    }

    @Override
    public void clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        GL20.glDrawArrays(toNative(topology), firstVertex, vertexCount);
    }

    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        GL31.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.position(0);
        pixels.limit(width * height * 4);
        return pixels;
    }

    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        GL11.glDrawElements(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes);
    }

    @Override
    public void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        GL31.glDrawElementsInstanced(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT,
                offsetBytes, instanceCount);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return GL11.GL_LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return GL11.GL_TRIANGLE_STRIP;
        }
        return GL11.GL_TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return GL11.GL_REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return GL14.GL_MIRRORED_REPEAT;
        }
        return GL12.GL_CLAMP_TO_EDGE;
    }
}
