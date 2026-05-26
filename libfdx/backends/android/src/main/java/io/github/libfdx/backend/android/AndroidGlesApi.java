package io.github.libfdx.backend.android;

import android.opengl.GLES30;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

final class AndroidGlesApi implements GLApi {
    private final Map<Integer, GLShaderType> shaderTypes = new HashMap<Integer, GLShaderType>();

    @Override
    public int createProgram() {
        return GLES30.glCreateProgram();
    }

    @Override
    public int createShader(GLShaderType type) {
        int shader;
        if (type == GLShaderType.VERTEX) {
            shader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER);
        } else if (type == GLShaderType.FRAGMENT) {
            shader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER);
        } else {
            throw new FdxException("Unsupported GLES shader type: " + type);
        }
        shaderTypes.put(shader, type);
        return shader;
    }

    @Override
    public void shaderSource(int shader, String source) {
        GLES30.glShaderSource(shader, toGlesSource(shaderTypes.get(shader), source));
    }

    @Override
    public void compileShader(int shader) {
        GLES30.glCompileShader(shader);
    }

    @Override
    public boolean shaderCompileStatus(int shader) {
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        return status[0] != GLES30.GL_FALSE;
    }

    @Override
    public String shaderInfoLog(int shader) {
        return GLES30.glGetShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        shaderTypes.remove(shader);
        GLES30.glDeleteShader(shader);
    }

    @Override
    public void attachShader(int program, int shader) {
        GLES30.glAttachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GLES30.glLinkProgram(program);
    }

    @Override
    public boolean programLinkStatus(int program) {
        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        return status[0] != GLES30.GL_FALSE;
    }

    @Override
    public String programInfoLog(int program) {
        return GLES30.glGetProgramInfoLog(program);
    }

    @Override
    public void deleteProgram(int program) {
        GLES30.glDeleteProgram(program);
    }

    @Override
    public void useProgram(int program) {
        GLES30.glUseProgram(program);
    }

    @Override
    public int genVertexArray() {
        int[] vertexArrays = new int[1];
        GLES30.glGenVertexArrays(1, vertexArrays, 0);
        return vertexArrays[0];
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        GLES30.glBindVertexArray(vertexArray);
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        int[] vertexArrays = {vertexArray};
        GLES30.glDeleteVertexArrays(1, vertexArrays, 0);
    }

    @Override
    public int genBuffer() {
        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        return buffers[0];
    }

    @Override
    public void bindArrayBuffer(int buffer) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer);
    }

    @Override
    public void bindElementArrayBuffer(int buffer) {
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    @Override
    public void bufferData(int size) {
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, null, GLES30.GL_DYNAMIC_DRAW);
    }

    @Override
    public void elementBufferData(int size) {
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, size, null, GLES30.GL_DYNAMIC_DRAW);
    }

    @Override
    public void bufferSubData(ByteBuffer data) {
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public void elementBufferSubData(ByteBuffer data) {
        GLES30.glBufferSubData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public void deleteBuffer(int buffer) {
        int[] buffers = {buffer};
        GLES30.glDeleteBuffers(1, buffers, 0);
    }

    @Override
    public int genTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        return textures[0];
    }

    @Override
    public void bindTexture2D(int texture) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
    }

    @Override
    public void texImage2D(int width, int height, ByteBuffer data) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data);
    }

    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data);
    }

    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, toNative(wrapS));
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, toNative(wrapT));
    }

    @Override
    public void deleteTexture(int texture) {
        int[] textures = {texture};
        GLES30.glDeleteTextures(1, textures, 0);
    }

    @Override
    public void activeTexture(int slot) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + slot);
    }

    @Override
    public int uniformLocation(int program, String name) {
        return GLES30.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int value) {
        GLES30.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        GLES30.glUniform1f(location, value);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        GLES30.glUniform3f(location, x, y, z);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GLES30.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        GLES30.glUniformMatrix4fv(location, 1, transpose, values, 0);
    }

    @Override
    public void enableAlphaBlending() {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        } else {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        }
    }

    @Override
    public void depthMask(boolean enabled) {
        GLES30.glDepthMask(enabled);
    }

    @Override
    public void depthFuncLessEqual() {
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GLES30.glEnableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GLES30.glViewport(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        GLES30.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearColorBuffer() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void clearDepth(float depth) {
        GLES30.glClearDepthf(depth);
    }

    @Override
    public void clearDepthBuffer() {
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        GLES30.glDrawArrays(toNative(topology), firstVertex, vertexCount);
    }

    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        GLES30.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        pixels.position(0);
        pixels.limit(width * height * 4);
        return pixels;
    }

    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        GLES30.glDrawElements(toNative(topology), indexCount, GLES30.GL_UNSIGNED_SHORT, offsetBytes);
    }

    @Override
    public void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        GLES30.glDrawElementsInstanced(toNative(topology), indexCount, GLES30.GL_UNSIGNED_SHORT,
                offsetBytes, instanceCount);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return GLES30.GL_LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return GLES30.GL_TRIANGLE_STRIP;
        }
        return GLES30.GL_TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return GLES30.GL_REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return GLES30.GL_MIRRORED_REPEAT;
        }
        return GLES30.GL_CLAMP_TO_EDGE;
    }

    private String toGlesSource(GLShaderType type, String source) {
        String actualSource = source != null ? source : "";
        if (actualSource.startsWith("#version 330 core")) {
            actualSource = "#version 300 es" + actualSource.substring("#version 330 core".length());
        } else if (actualSource.startsWith("#version 330")) {
            actualSource = "#version 300 es" + actualSource.substring("#version 330".length());
        } else if (!actualSource.startsWith("#version 300 es")) {
            actualSource = "#version 300 es\n" + actualSource;
        }
        if (type == GLShaderType.FRAGMENT && actualSource.indexOf("precision ") < 0) {
            int lineEnd = actualSource.indexOf('\n');
            if (lineEnd >= 0) {
                actualSource = actualSource.substring(0, lineEnd + 1)
                        + "precision highp float;\n"
                        + actualSource.substring(lineEnd + 1);
            } else {
                actualSource = actualSource + "\nprecision highp float;\n";
            }
        }
        return actualSource;
    }
}
