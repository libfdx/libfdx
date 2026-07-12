package io.github.libfdx.backend.iosc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;
import org.teavm.interop.Address;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes API access for iOS C GLES.
 *
 * @author xpenatan
 */
final class IosCGLApi implements GLApi {
    private final Map<Integer, GLShaderType> shaderTypes = new HashMap<Integer, GLShaderType>();

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return IosCOpenGLES.glCreateProgram();
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
            shader = IosCOpenGLES.glCreateShader(IosCOpenGLES.VERTEX_SHADER);
        } else if (type == GLShaderType.FRAGMENT) {
            shader = IosCOpenGLES.glCreateShader(IosCOpenGLES.FRAGMENT_SHADER);
        } else {
            throw new FdxException("Unsupported iOS GLES shader type: " + type);
        }
        shaderTypes.put(Integer.valueOf(shader), type);
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
        byte[] sourceBytes = toGlesSource(shaderTypes.get(Integer.valueOf(shader)), source)
                .getBytes(StandardCharsets.UTF_8);
        int[] sourceData = new int[sourceBytes.length];
        for (int i = 0; i < sourceBytes.length; i++) {
            sourceData[i] = sourceBytes[i] & 0xff;
        }
        IosCOpenGLES.libfdxIosCGlShaderSource(shader, sourceData.length, Address.ofData(sourceData));
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        IosCOpenGLES.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return IosCOpenGLES.getShaderInt(shader, IosCOpenGLES.COMPILE_STATUS) != IosCOpenGLES.FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return IosCOpenGLES.getShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        shaderTypes.remove(Integer.valueOf(shader));
        IosCOpenGLES.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        IosCOpenGLES.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        IosCOpenGLES.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return IosCOpenGLES.getProgramInt(program, IosCOpenGLES.LINK_STATUS) != IosCOpenGLES.FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return IosCOpenGLES.getProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        IosCOpenGLES.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        IosCOpenGLES.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return IosCOpenGLES.genVertexArray();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        IosCOpenGLES.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        IosCOpenGLES.deleteVertexArray(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return IosCOpenGLES.genBuffer();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        IosCOpenGLES.glBindBuffer(IosCOpenGLES.ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        IosCOpenGLES.glBindBuffer(IosCOpenGLES.ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        IosCOpenGLES.glBufferData(IosCOpenGLES.ARRAY_BUFFER, size, Address.fromLong(0L),
                IosCOpenGLES.DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        IosCOpenGLES.glBufferData(IosCOpenGLES.ELEMENT_ARRAY_BUFFER, size, Address.fromLong(0L),
                IosCOpenGLES.DYNAMIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        IosCOpenGLES.glBufferSubData(IosCOpenGLES.ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        IosCOpenGLES.glBindBuffer(IosCOpenGLES.UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        IosCOpenGLES.glBufferData(IosCOpenGLES.UNIFORM_BUFFER, size, Address.fromLong(0L),
                IosCOpenGLES.DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        IosCOpenGLES.glBufferSubData(IosCOpenGLES.UNIFORM_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        IosCOpenGLES.glBindBufferBase(IosCOpenGLES.UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        IosCOpenGLES.glBufferSubData(IosCOpenGLES.ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        IosCOpenGLES.deleteBuffer(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return IosCOpenGLES.genTexture();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        IosCOpenGLES.glBindTexture(IosCOpenGLES.TEXTURE_2D, texture);
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
        IosCOpenGLES.glPixelStorei(IosCOpenGLES.UNPACK_ALIGNMENT, 1);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_MIN_FILTER,
                IosCOpenGLES.LINEAR);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_MAG_FILTER,
                IosCOpenGLES.LINEAR);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_WRAP_S,
                IosCOpenGLES.CLAMP_TO_EDGE);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_WRAP_T,
                IosCOpenGLES.CLAMP_TO_EDGE);
        IosCOpenGLES.glTexImage2D(IosCOpenGLES.TEXTURE_2D, 0, IosCOpenGLES.RGBA, width, height, 0,
                IosCOpenGLES.RGBA, IosCOpenGLES.UNSIGNED_BYTE, Address.fromLong(0L));
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
        IosCOpenGLES.glPixelStorei(IosCOpenGLES.UNPACK_ALIGNMENT, 1);
        IosCOpenGLES.glTexSubImage2D(IosCOpenGLES.TEXTURE_2D, 0, 0, 0, width, height,
                IosCOpenGLES.RGBA, IosCOpenGLES.UNSIGNED_BYTE, data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_WRAP_S, toNative(wrapS));
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_MIN_FILTER, nativeFilter);
        IosCOpenGLES.glTexParameteri(IosCOpenGLES.TEXTURE_2D, IosCOpenGLES.TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        IosCOpenGLES.deleteTexture(texture);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        IosCOpenGLES.glActiveTexture(IosCOpenGLES.TEXTURE0 + slot);
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
        return IosCOpenGLES.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        IosCOpenGLES.glUniform1i(location, value);
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
        return IosCOpenGLES.glGetUniformBlockIndex(program, name);
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
        IosCOpenGLES.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        IosCOpenGLES.glUniform1f(location, value);
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
        IosCOpenGLES.glUniform3f(location, x, y, z);
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
        IosCOpenGLES.glUniform4f(location, x, y, z, w);
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
        IosCOpenGLES.glUniformMatrix4fv(location, 1, transpose, values);
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        IosCOpenGLES.glEnable(IosCOpenGLES.BLEND);
        IosCOpenGLES.glBlendFuncSeparate(IosCOpenGLES.SRC_ALPHA, IosCOpenGLES.ONE_MINUS_SRC_ALPHA,
                IosCOpenGLES.ONE, IosCOpenGLES.ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            IosCOpenGLES.glEnable(IosCOpenGLES.DEPTH_TEST);
        } else {
            IosCOpenGLES.glDisable(IosCOpenGLES.DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        IosCOpenGLES.glDepthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        IosCOpenGLES.glDepthFunc(IosCOpenGLES.LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        IosCOpenGLES.glEnableVertexAttribArray(index);
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
        IosCOpenGLES.glVertexAttribPointer(index, size, IosCOpenGLES.FLOAT, false, stride, offset);
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
            IosCOpenGLES.glVertexAttribPointer(index, format.componentCount(), IosCOpenGLES.UNSIGNED_BYTE,
                    true, stride, offset);
            return;
        }
        IosCOpenGLES.glVertexAttribPointer(index, format.componentCount(), IosCOpenGLES.FLOAT,
                false, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        IosCOpenGLES.glVertexAttribDivisor(index, divisor);
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
        IosCOpenGLES.glViewport(x, y, width, height);
    }

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            IosCOpenGLES.glEnable(IosCOpenGLES.SCISSOR_TEST);
        } else {
            IosCOpenGLES.glDisable(IosCOpenGLES.SCISSOR_TEST);
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
        IosCOpenGLES.glScissor(x, y, width, height);
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
        IosCOpenGLES.glClearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        IosCOpenGLES.glClear(IosCOpenGLES.COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        IosCOpenGLES.glClearDepthf(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        IosCOpenGLES.glClear(IosCOpenGLES.DEPTH_BUFFER_BIT);
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
        IosCOpenGLES.glDrawArrays(toNative(topology), firstVertex, vertexCount);
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
        IosCOpenGLES.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
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
        IosCOpenGLES.glReadPixels(0, 0, width, height, IosCOpenGLES.RGBA, IosCOpenGLES.UNSIGNED_BYTE, pixels);
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
        IosCOpenGLES.glDrawElements(toNative(topology), indexCount, IosCOpenGLES.UNSIGNED_SHORT,
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
        IosCOpenGLES.glDrawElementsInstanced(toNative(topology), indexCount, IosCOpenGLES.UNSIGNED_SHORT,
                Address.fromLong(offsetBytes), instanceCount);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return IosCOpenGLES.LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return IosCOpenGLES.TRIANGLE_STRIP;
        }
        return IosCOpenGLES.TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return IosCOpenGLES.REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return IosCOpenGLES.MIRRORED_REPEAT;
        }
        return IosCOpenGLES.CLAMP_TO_EDGE;
    }

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? IosCOpenGLES.NEAREST : IosCOpenGLES.LINEAR;
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
