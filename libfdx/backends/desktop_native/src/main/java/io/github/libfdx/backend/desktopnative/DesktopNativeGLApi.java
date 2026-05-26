package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;
import org.teavm.interop.Address;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class DesktopNativeGLApi implements GLApi {
    @Override
    public int createProgram() {
        return DesktopNativeOpenGL.glCreateProgram();
    }

    @Override
    public int createShader(GLShaderType type) {
        if (type == GLShaderType.VERTEX) {
            return DesktopNativeOpenGL.glCreateShader(DesktopNativeOpenGL.VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return DesktopNativeOpenGL.glCreateShader(DesktopNativeOpenGL.FRAGMENT_SHADER);
        }
        throw new FdxException("Unsupported GL shader type: " + type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] sourceCString = new byte[sourceBytes.length + 1];
        System.arraycopy(sourceBytes, 0, sourceCString, 0, sourceBytes.length);
        byte[] sourcePointer = new byte[Address.sizeOf()];
        Address strings = Address.ofData(sourcePointer);
        strings.putAddress(Address.ofData(sourceCString));
        DesktopNativeOpenGL.glShaderSource(shader, 1, strings, Address.fromLong(0L));
    }

    @Override
    public void compileShader(int shader) {
        DesktopNativeOpenGL.glCompileShader(shader);
    }

    @Override
    public boolean shaderCompileStatus(int shader) {
        return DesktopNativeOpenGL.getShaderInt(shader, DesktopNativeOpenGL.COMPILE_STATUS) != DesktopNativeOpenGL.FALSE;
    }

    @Override
    public String shaderInfoLog(int shader) {
        return DesktopNativeOpenGL.getShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        DesktopNativeOpenGL.glDeleteShader(shader);
    }

    @Override
    public void attachShader(int program, int shader) {
        DesktopNativeOpenGL.glAttachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        DesktopNativeOpenGL.glLinkProgram(program);
    }

    @Override
    public boolean programLinkStatus(int program) {
        return DesktopNativeOpenGL.getProgramInt(program, DesktopNativeOpenGL.LINK_STATUS) != DesktopNativeOpenGL.FALSE;
    }

    @Override
    public String programInfoLog(int program) {
        return DesktopNativeOpenGL.getProgramInfoLog(program);
    }

    @Override
    public void deleteProgram(int program) {
        DesktopNativeOpenGL.glDeleteProgram(program);
    }

    @Override
    public void useProgram(int program) {
        DesktopNativeOpenGL.glUseProgram(program);
    }

    @Override
    public int genVertexArray() {
        return DesktopNativeOpenGL.genVertexArray();
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        DesktopNativeOpenGL.glBindVertexArray(vertexArray);
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        DesktopNativeOpenGL.deleteVertexArray(vertexArray);
    }

    @Override
    public int genBuffer() {
        return DesktopNativeOpenGL.genBuffer();
    }

    @Override
    public void bindArrayBuffer(int buffer) {
        DesktopNativeOpenGL.glBindBuffer(DesktopNativeOpenGL.ARRAY_BUFFER, buffer);
    }

    @Override
    public void bindElementArrayBuffer(int buffer) {
        DesktopNativeOpenGL.glBindBuffer(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, buffer);
    }

    @Override
    public void bufferData(int size) {
        DesktopNativeOpenGL.glBufferData(DesktopNativeOpenGL.ARRAY_BUFFER, size, Address.fromLong(0L),
                DesktopNativeOpenGL.DYNAMIC_DRAW);
    }

    @Override
    public void elementBufferData(int size) {
        DesktopNativeOpenGL.glBufferData(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, size, Address.fromLong(0L),
                DesktopNativeOpenGL.STATIC_DRAW);
    }

    @Override
    public void bufferSubData(ByteBuffer data) {
        DesktopNativeOpenGL.glBufferSubData(DesktopNativeOpenGL.ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public void elementBufferSubData(ByteBuffer data) {
        DesktopNativeOpenGL.glBufferSubData(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public void deleteBuffer(int buffer) {
        DesktopNativeOpenGL.deleteBuffer(buffer);
    }

    @Override
    public int genTexture() {
        return DesktopNativeOpenGL.genTexture();
    }

    @Override
    public void bindTexture2D(int texture) {
        DesktopNativeOpenGL.glBindTexture(DesktopNativeOpenGL.TEXTURE_2D, texture);
    }

    @Override
    public void texImage2D(int width, int height, ByteBuffer data) {
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_MIN_FILTER,
                DesktopNativeOpenGL.LINEAR);
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_MAG_FILTER,
                DesktopNativeOpenGL.LINEAR);
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_S,
                DesktopNativeOpenGL.CLAMP_TO_EDGE);
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_T,
                DesktopNativeOpenGL.CLAMP_TO_EDGE);
        DesktopNativeOpenGL.glTexImage2D(DesktopNativeOpenGL.TEXTURE_2D, 0, DesktopNativeOpenGL.RGBA8, width, height, 0,
                DesktopNativeOpenGL.RGBA, DesktopNativeOpenGL.UNSIGNED_BYTE, Address.fromLong(0L));
        if (data != null) {
            texSubImage2D(width, height, data);
        }
    }

    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        DesktopNativeOpenGL.glTexSubImage2D(DesktopNativeOpenGL.TEXTURE_2D, 0, 0, 0, width, height,
                DesktopNativeOpenGL.RGBA, DesktopNativeOpenGL.UNSIGNED_BYTE, data);
    }

    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_S,
                toNative(wrapS));
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_T,
                toNative(wrapT));
    }

    @Override
    public void deleteTexture(int texture) {
        DesktopNativeOpenGL.deleteTexture(texture);
    }

    @Override
    public void activeTexture(int slot) {
        DesktopNativeOpenGL.glActiveTexture(DesktopNativeOpenGL.TEXTURE0 + slot);
    }

    @Override
    public int uniformLocation(int program, String name) {
        return DesktopNativeOpenGL.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int value) {
        DesktopNativeOpenGL.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        DesktopNativeOpenGL.glUniform1f(location, value);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        DesktopNativeOpenGL.glUniform3f(location, x, y, z);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        DesktopNativeOpenGL.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        DesktopNativeOpenGL.glUniformMatrix4fv(location, 1, transpose, values);
    }

    @Override
    public void enableAlphaBlending() {
        DesktopNativeOpenGL.glEnable(DesktopNativeOpenGL.BLEND);
        DesktopNativeOpenGL.glBlendFunc(DesktopNativeOpenGL.SRC_ALPHA, DesktopNativeOpenGL.ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            DesktopNativeOpenGL.glEnable(DesktopNativeOpenGL.DEPTH_TEST);
        } else {
            DesktopNativeOpenGL.glDisable(DesktopNativeOpenGL.DEPTH_TEST);
        }
    }

    @Override
    public void depthMask(boolean enabled) {
        DesktopNativeOpenGL.glDepthMask(enabled);
    }

    @Override
    public void depthFuncLessEqual() {
        DesktopNativeOpenGL.glDepthFunc(DesktopNativeOpenGL.LEQUAL);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        DesktopNativeOpenGL.glEnableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        DesktopNativeOpenGL.glVertexAttribPointer(index, size, DesktopNativeOpenGL.FLOAT, false, stride, offset);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        DesktopNativeOpenGL.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        DesktopNativeOpenGL.glViewport(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        DesktopNativeOpenGL.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearColorBuffer() {
        DesktopNativeOpenGL.glClear(DesktopNativeOpenGL.COLOR_BUFFER_BIT);
    }

    @Override
    public void clearDepth(float depth) {
        DesktopNativeOpenGL.glClearDepth(depth);
    }

    @Override
    public void clearDepthBuffer() {
        DesktopNativeOpenGL.glClear(DesktopNativeOpenGL.DEPTH_BUFFER_BIT);
    }

    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        DesktopNativeOpenGL.glDrawArrays(toNative(topology), firstVertex, vertexCount);
    }

    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        DesktopNativeOpenGL.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        DesktopNativeOpenGL.glDrawElements(toNative(topology), indexCount, DesktopNativeOpenGL.UNSIGNED_SHORT,
                Address.fromLong(offsetBytes));
    }

    @Override
    public void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        DesktopNativeOpenGL.glDrawElementsInstanced(toNative(topology), indexCount,
                DesktopNativeOpenGL.UNSIGNED_SHORT, Address.fromLong(offsetBytes), instanceCount);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return DesktopNativeOpenGL.LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return DesktopNativeOpenGL.TRIANGLE_STRIP;
        }
        return DesktopNativeOpenGL.TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return DesktopNativeOpenGL.REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return DesktopNativeOpenGL.MIRRORED_REPEAT;
        }
        return DesktopNativeOpenGL.CLAMP_TO_EDGE;
    }
}
