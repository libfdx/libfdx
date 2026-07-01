package io.github.libfdx.graphics.gl;

import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Defines the contract for GL api implementations.
 *
 * @author xpenatan
 */
public interface GLApi {
    /**
     * Returns the create program.
     *
     * @return the created value
     */
    int createProgram();

    /**
     * Creates a shader.
     *
     * @param type the expected Java type
     * @return the created value
     */
    int createShader(GLShaderType type);

    /**
     * Runs the shader source step.
     *
     * @param shader the shader
     * @param source the source value
     */
    void shaderSource(int shader, String source);

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    void compileShader(int shader);

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    boolean shaderCompileStatus(int shader);

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    String shaderInfoLog(int shader);

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    void deleteShader(int shader);

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    void attachShader(int program, int shader);

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    void linkProgram(int program);

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    boolean programLinkStatus(int program);

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    String programInfoLog(int program);

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    void deleteProgram(int program);

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    void useProgram(int program);

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    int genVertexArray();

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    void bindVertexArray(int vertexArray);

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    void deleteVertexArray(int vertexArray);

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    int genBuffer();

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    void bindArrayBuffer(int buffer);

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    default void bindElementArrayBuffer(int buffer) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    void bufferData(int size);

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    default void elementBufferData(int size) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    void bufferSubData(ByteBuffer data);

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    default void bindUniformBuffer(int buffer) {
        throw new UnsupportedOperationException("Uniform buffers are not supported");
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    default void uniformBufferData(int size) {
        throw new UnsupportedOperationException("Uniform buffers are not supported");
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    default void uniformBufferSubData(ByteBuffer data) {
        throw new UnsupportedOperationException("Uniform buffers are not supported");
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    default void bindUniformBufferBase(int binding, int buffer) {
        throw new UnsupportedOperationException("Uniform buffers are not supported");
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    default void elementBufferSubData(ByteBuffer data) {
        throw new UnsupportedOperationException("Element array buffers are not supported");
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    void deleteBuffer(int buffer);

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    int genTexture();

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    void bindTexture2D(int texture);

    /**
     * Runs the tex image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    void texImage2D(int width, int height, ByteBuffer data);

    /**
     * Runs the tex sub image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    void texSubImage2D(int width, int height, ByteBuffer data);

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    void deleteTexture(int texture);

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    default void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    default void textureFilter2D(TextureFilter filter) {
    }

    /**
     * Returns the gen framebuffer.
     *
     * @return the gen framebuffer
     */
    default int genFramebuffer() {
        throw new UnsupportedOperationException("Framebuffers are not supported");
    }

    /**
     * Runs the bind framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    default void bindFramebuffer(int framebuffer) {
        throw new UnsupportedOperationException("Framebuffers are not supported");
    }

    /**
     * Runs the framebuffer texture2 d step.
     *
     * @param texture the texture
     */
    default void framebufferTexture2D(int texture) {
        throw new UnsupportedOperationException("Framebuffers are not supported");
    }

    /**
     * Returns whether the currently bound framebuffer is complete.
     *
     * @return true if complete
     */
    default boolean framebufferComplete() {
        throw new UnsupportedOperationException("Framebuffers are not supported");
    }

    /**
     * Runs the delete framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    default void deleteFramebuffer(int framebuffer) {
        throw new UnsupportedOperationException("Framebuffers are not supported");
    }

    /**
     * Returns the gen renderbuffer.
     *
     * @return the gen renderbuffer
     */
    default int genRenderbuffer() {
        throw new UnsupportedOperationException("Renderbuffers are not supported");
    }

    /**
     * Runs the bind renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    default void bindRenderbuffer(int renderbuffer) {
        throw new UnsupportedOperationException("Renderbuffers are not supported");
    }

    /**
     * Runs the renderbuffer depth storage step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    default void renderbufferStorageDepth(int width, int height) {
        throw new UnsupportedOperationException("Renderbuffers are not supported");
    }

    /**
     * Runs the framebuffer depth renderbuffer attachment step.
     *
     * @param renderbuffer the renderbuffer
     */
    default void framebufferRenderbufferDepth(int renderbuffer) {
        throw new UnsupportedOperationException("Renderbuffers are not supported");
    }

    /**
     * Runs the delete renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    default void deleteRenderbuffer(int renderbuffer) {
        throw new UnsupportedOperationException("Renderbuffers are not supported");
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    void activeTexture(int slot);

    /**
     * Runs the uniform location step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform location
     */
    int uniformLocation(int program, String name);

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    void uniform1i(int location, int value);

    /**
     * Runs the uniform block index step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform block index, or -1 when absent
     */
    default int uniformBlockIndex(int program, String name) {
        return -1;
    }

    /**
     * Runs the uniform block binding step.
     *
     * @param program the program
     * @param blockIndex the block index
     * @param binding the binding
     */
    default void uniformBlockBinding(int program, int blockIndex, int binding) {
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    default void uniform1f(int location, float value) {
    }

    /**
     * Runs the uniform3f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    default void uniform3f(int location, float x, float y, float z) {
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
    default void uniform4f(int location, float x, float y, float z, float w) {
    }

    /**
     * Runs the uniform matrix4fv step.
     *
     * @param location the location
     * @param transpose the transpose
     * @param values the values
     */
    default void uniformMatrix4fv(int location, boolean transpose, float[] values) {
    }

    /**
     * Runs the enable alpha blending step.
     */
    void enableAlphaBlending();

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    default void enableDepthTest(boolean enabled) {
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    default void depthMask(boolean enabled) {
    }

    /**
     * Runs the depth func less equal step.
     */
    default void depthFuncLessEqual() {
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    void enableVertexAttribArray(int index);

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param size the size
     * @param stride the stride
     * @param offset the offset
     */
    void vertexAttribPointer(int index, int size, int stride, int offset);

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param format the format
     * @param stride the stride
     * @param offset the offset
     */
    default void vertexAttribPointer(int index, VertexFormat format, int stride, int offset) {
        vertexAttribPointer(index, format.componentCount(), stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    default void vertexAttribDivisor(int index, int divisor) {
        if (divisor != 0) {
            throw new UnsupportedOperationException("Instanced vertex attributes are not supported");
        }
    }

    /**
     * Runs the viewport step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    void viewport(int x, int y, int width, int height);

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    default void enableScissorTest(boolean enabled) {
    }

    /**
     * Runs the scissor step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    default void scissor(int x, int y, int width, int height) {
    }

    /**
     * Runs the clear color step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    void clearColor(float red, float green, float blue, float alpha);

    /**
     * Runs the clear color buffer step.
     */
    void clearColorBuffer();

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    default void clearDepth(float depth) {
    }

    /**
     * Runs the clear depth buffer step.
     */
    default void clearDepthBuffer() {
    }

    /**
     * Draws arrays.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount);

    /**
     * Draws arrays instanced.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     */
    void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount);

    /**
     * Runs the read pixels RGBA8 step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the read pixels RGBA8
     */
    default ByteBuffer readPixelsRgba8(int width, int height) {
        throw new UnsupportedOperationException("Framebuffer readback is not supported");
    }

    /**
     * Draws elements.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     */
    default void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        throw new UnsupportedOperationException("Indexed draws are not supported");
    }

    /**
     * Draws elements base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param baseVertex the base vertex
     */
    default void drawElementsBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes, int baseVertex) {
        if (baseVertex != 0) {
            throw new UnsupportedOperationException("Base-vertex indexed draws are not supported");
        }
        drawElements(topology, indexCount, offsetBytes);
    }

    /**
     * Draws elements instanced.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     */
    default void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        throw new UnsupportedOperationException("Indexed draws are not supported");
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
    default void drawElementsInstancedBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes,
            int instanceCount, int baseVertex) {
        if (baseVertex != 0) {
            throw new UnsupportedOperationException("Base-vertex indexed draws are not supported");
        }
        drawElementsInstanced(topology, indexCount, offsetBytes, instanceCount);
    }
}
