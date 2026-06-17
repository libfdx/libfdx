package io.github.libfdx.backend.android;

import android.opengl.GLES30;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes API access for android gles.
 *
 * @author xpenatan
 */
final class AndroidGlesApi implements GLApi {
    private final Map<Integer, GLShaderType> shaderTypes = new HashMap<Integer, GLShaderType>();

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return GLES30.glCreateProgram();
    }

    /**
     * Creates a shader.
     *
     * @param type the expected Java type
     * @return the created value
     */
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

    /**
     * Runs the shader source step.
     *
     * @param shader the shader
     * @param source the source value
     */
    @Override
    public void shaderSource(int shader, String source) {
        GLES30.glShaderSource(shader, toGlesSource(shaderTypes.get(shader), source));
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        GLES30.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        return status[0] != GLES30.GL_FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return GLES30.glGetShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        shaderTypes.remove(shader);
        GLES30.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        GLES30.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        GLES30.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        return status[0] != GLES30.GL_FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return GLES30.glGetProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        GLES30.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        GLES30.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        int[] vertexArrays = new int[1];
        GLES30.glGenVertexArrays(1, vertexArrays, 0);
        return vertexArrays[0];
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        GLES30.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        int[] vertexArrays = {vertexArray};
        GLES30.glDeleteVertexArrays(1, vertexArrays, 0);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        return buffers[0];
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, null, GLES30.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, size, null, GLES30.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, size, null, GLES30.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        GLES30.glBufferSubData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        int[] buffers = {buffer};
        GLES30.glDeleteBuffers(1, buffers, 0);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        return textures[0];
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
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
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data);
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
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, toNative(wrapS));
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        int[] textures = {texture};
        GLES30.glDeleteTextures(1, textures, 0);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + slot);
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
        return GLES30.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        GLES30.glUniform1i(location, value);
    }

    /**
     * Runs the uniform block index step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform block index, or -1 when absent
     */
    @Override
    public int uniformBlockIndex(int program, String name) {
        int index = GLES30.glGetUniformBlockIndex(program, name);
        return index == GLES30.GL_INVALID_INDEX ? -1 : index;
    }

    /**
     * Runs the uniform block binding step.
     *
     * @param program the program
     * @param blockIndex the block index
     * @param binding the binding
     */
    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        GLES30.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        GLES30.glUniform1f(location, value);
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
        GLES30.glUniform3f(location, x, y, z);
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
        GLES30.glUniform4f(location, x, y, z, w);
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
        GLES30.glUniformMatrix4fv(location, 1, transpose, values, 0);
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        } else {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        GLES30.glDepthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        GLES30.glEnableVertexAttribArray(index);
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
        GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param format the format
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, VertexFormat format, int stride, int offset) {
        if (format == VertexFormat.UNORM8X4) {
            GLES30.glVertexAttribPointer(index, format.componentCount(), GLES30.GL_UNSIGNED_BYTE, true,
                    stride, offset);
            return;
        }
        GLES30.glVertexAttribPointer(index, format.componentCount(), GLES30.GL_FLOAT, false, stride, offset);
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
        GLES30.glViewport(x, y, width, height);
    }

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
        } else {
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        }
    }

    /**
     * Runs the scissor step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void scissor(int x, int y, int width, int height) {
        GLES30.glScissor(x, y, width, height);
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
        GLES30.glClearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        GLES30.glClearDepthf(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
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
        GLES30.glDrawArrays(toNative(topology), firstVertex, vertexCount);
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
        GLES30.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    /**
     * Runs the read pixels RGBA8 step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the read pixels RGBA8
     */
    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        pixels.position(0);
        pixels.limit(width * height * 4);
        return pixels;
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
        GLES30.glDrawElements(toNative(topology), indexCount, GLES30.GL_UNSIGNED_SHORT, offsetBytes);
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
