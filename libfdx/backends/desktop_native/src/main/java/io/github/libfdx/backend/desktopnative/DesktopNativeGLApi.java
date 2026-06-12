package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;
import org.teavm.interop.Address;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Exposes API access for desktop native GL.
 *
 * @author xpenatan
 */
final class DesktopNativeGLApi implements GLApi {
    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return DesktopNativeOpenGL.glCreateProgram();
    }

    /**
     * Creates a shader.
     *
     * @param type the expected Java type
     * @return the created value
     */
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

    /**
     * Runs the shader source step.
     *
     * @param shader the shader
     * @param source the source value
     */
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

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        DesktopNativeOpenGL.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return DesktopNativeOpenGL.getShaderInt(shader, DesktopNativeOpenGL.COMPILE_STATUS) != DesktopNativeOpenGL.FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return DesktopNativeOpenGL.getShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        DesktopNativeOpenGL.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        DesktopNativeOpenGL.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        DesktopNativeOpenGL.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return DesktopNativeOpenGL.getProgramInt(program, DesktopNativeOpenGL.LINK_STATUS) != DesktopNativeOpenGL.FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return DesktopNativeOpenGL.getProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        DesktopNativeOpenGL.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        DesktopNativeOpenGL.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return DesktopNativeOpenGL.genVertexArray();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        DesktopNativeOpenGL.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        DesktopNativeOpenGL.deleteVertexArray(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return DesktopNativeOpenGL.genBuffer();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        DesktopNativeOpenGL.glBindBuffer(DesktopNativeOpenGL.ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        DesktopNativeOpenGL.glBindBuffer(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        DesktopNativeOpenGL.glBufferData(DesktopNativeOpenGL.ARRAY_BUFFER, size, Address.fromLong(0L),
                DesktopNativeOpenGL.DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        DesktopNativeOpenGL.glBufferData(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, size, Address.fromLong(0L),
                DesktopNativeOpenGL.STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        DesktopNativeOpenGL.glBufferSubData(DesktopNativeOpenGL.ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        DesktopNativeOpenGL.glBufferSubData(DesktopNativeOpenGL.ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        DesktopNativeOpenGL.deleteBuffer(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return DesktopNativeOpenGL.genTexture();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        DesktopNativeOpenGL.glBindTexture(DesktopNativeOpenGL.TEXTURE_2D, texture);
    }

    /**
     * Runs the tex image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
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

    /**
     * Runs the tex sub image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        DesktopNativeOpenGL.glTexSubImage2D(DesktopNativeOpenGL.TEXTURE_2D, 0, 0, 0, width, height,
                DesktopNativeOpenGL.RGBA, DesktopNativeOpenGL.UNSIGNED_BYTE, data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_S,
                toNative(wrapS));
        DesktopNativeOpenGL.glTexParameteri(DesktopNativeOpenGL.TEXTURE_2D, DesktopNativeOpenGL.TEXTURE_WRAP_T,
                toNative(wrapT));
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        DesktopNativeOpenGL.deleteTexture(texture);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        DesktopNativeOpenGL.glActiveTexture(DesktopNativeOpenGL.TEXTURE0 + slot);
    }

    /**
     * Runs the uniform location step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform location
     */
    @Override
    public int uniformLocation(int program, String name) {
        return DesktopNativeOpenGL.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        DesktopNativeOpenGL.glUniform1i(location, value);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        DesktopNativeOpenGL.glUniform1f(location, value);
    }

    /**
     * Runs the uniform3f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    @Override
    public void uniform3f(int location, float x, float y, float z) {
        DesktopNativeOpenGL.glUniform3f(location, x, y, z);
    }

    /**
     * Runs the uniform4f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        DesktopNativeOpenGL.glUniform4f(location, x, y, z, w);
    }

    /**
     * Runs the uniform matrix4fv step.
     *
     * @param location the location
     * @param transpose the transpose
     * @param values the values
     */
    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        DesktopNativeOpenGL.glUniformMatrix4fv(location, 1, transpose, values);
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        DesktopNativeOpenGL.glEnable(DesktopNativeOpenGL.BLEND);
        DesktopNativeOpenGL.glBlendFunc(DesktopNativeOpenGL.SRC_ALPHA, DesktopNativeOpenGL.ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            DesktopNativeOpenGL.glEnable(DesktopNativeOpenGL.DEPTH_TEST);
        } else {
            DesktopNativeOpenGL.glDisable(DesktopNativeOpenGL.DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        DesktopNativeOpenGL.glDepthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        DesktopNativeOpenGL.glDepthFunc(DesktopNativeOpenGL.LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        DesktopNativeOpenGL.glEnableVertexAttribArray(index);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param size the size
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        DesktopNativeOpenGL.glVertexAttribPointer(index, size, DesktopNativeOpenGL.FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        DesktopNativeOpenGL.glVertexAttribDivisor(index, divisor);
    }

    /**
     * Runs the viewport step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void viewport(int x, int y, int width, int height) {
        DesktopNativeOpenGL.glViewport(x, y, width, height);
    }

    /**
     * Runs the clear color step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        DesktopNativeOpenGL.glClearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        DesktopNativeOpenGL.glClear(DesktopNativeOpenGL.COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        DesktopNativeOpenGL.glClearDepth(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        DesktopNativeOpenGL.glClear(DesktopNativeOpenGL.DEPTH_BUFFER_BIT);
    }

    /**
     * Draws arrays.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        DesktopNativeOpenGL.glDrawArrays(toNative(topology), firstVertex, vertexCount);
    }

    /**
     * Draws arrays instanced.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     */
    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        DesktopNativeOpenGL.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    /**
     * Draws elements.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     */
    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        DesktopNativeOpenGL.glDrawElements(toNative(topology), indexCount, DesktopNativeOpenGL.UNSIGNED_SHORT,
                Address.fromLong(offsetBytes));
    }

    /**
     * Draws elements instanced.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     */
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
