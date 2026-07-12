package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
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
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

/**
 * Exposes API access for desktop GL.
 *
 * @author xpenatan
 */
final class DesktopGLApi implements GLApi {
    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
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
            return GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
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
        GL20.glShaderSource(shader, source);
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return GL30.glGenVertexArrays();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        GL30.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        GL30.glDeleteVertexArrays(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return GL15.glGenBuffers();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, size, GL15.GL_STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return GL11.glGenTextures();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
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
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, toNative(wrapS));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, nativeFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    /**
     * Returns the gen framebuffer.
     *
     * @return the gen framebuffer
     */
    @Override
    public int genFramebuffer() {
        return GL30.glGenFramebuffers();
    }

    /**
     * Runs the bind framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void bindFramebuffer(int framebuffer) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
    }

    /**
     * Runs the framebuffer texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void framebufferTexture2D(int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
    }

    /**
     * Returns whether the currently bound framebuffer is complete.
     *
     * @return true if complete
     */
    @Override
    public boolean framebufferComplete() {
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /**
     * Runs the delete framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void deleteFramebuffer(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    /**
     * Returns the gen renderbuffer.
     *
     * @return the gen renderbuffer
     */
    @Override
    public int genRenderbuffer() {
        return GL30.glGenRenderbuffers();
    }

    /**
     * Runs the bind renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void bindRenderbuffer(int renderbuffer) {
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
    }

    /**
     * Runs the renderbuffer depth storage step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void renderbufferStorageDepth(int width, int height) {
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
    }

    /**
     * Runs the framebuffer depth renderbuffer attachment step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void framebufferRenderbufferDepth(int renderbuffer) {
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, renderbuffer);
    }

    /**
     * Runs the delete renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        GL30.glDeleteRenderbuffers(renderbuffer);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot);
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
        return GL20.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
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
        return GL31.glGetUniformBlockIndex(program, name);
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
        GL31.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        GL20.glUniform1f(location, value);
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
        GL20.glUniform3f(location, x, y, z);
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
        GL20.glUniform4f(location, x, y, z, w);
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
        GL20.glUniformMatrix4fv(location, transpose, values);
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        GL11.glDepthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
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
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
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
            GL20.glVertexAttribPointer(index, format.componentCount(), GL11.GL_UNSIGNED_BYTE, true, stride, offset);
            return;
        }
        GL20.glVertexAttribPointer(index, format.componentCount(), GL11.GL_FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33.glVertexAttribDivisor(index, divisor);
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
        GL11.glViewport(x, y, width, height);
    }

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
        GL11.glScissor(x, y, width, height);
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
        GL11.glClearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        GL11.glClearDepth(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
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
        GL20.glDrawArrays(toNative(topology), firstVertex, vertexCount);
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
        GL31.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
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
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
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
        GL11.glDrawElements(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes);
    }

    /**
     * Draws elements base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes, int baseVertex) {
        GL32.glDrawElementsBaseVertex(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes, baseVertex);
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
        GL31.glDrawElementsInstanced(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT,
                offsetBytes, instanceCount);
    }

    /**
     * Draws elements instanced base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsInstancedBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes,
            int instanceCount, int baseVertex) {
        GL32.glDrawElementsInstancedBaseVertex(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes,
                instanceCount, baseVertex);
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

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }
}
