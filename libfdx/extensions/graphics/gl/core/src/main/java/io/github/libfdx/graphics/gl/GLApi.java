package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.GraphicsFrameMetrics;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureMipmapFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import java.nio.ByteBuffer;

/**
 * Defines the contract for GL api implementations.
 *
 * @author xpenatan
 */
public interface GLApi {
    /** Actual bounded CPU workers supplied by this platform adapter; zero means unavailable. */
    default int shaderPreparationWorkers() { return 0; }
    /** Soft CPU budget per loading advance, checked between source continuations. Zero disables
     * the time limit. This cannot preempt a generator or bound a native GL call. */
    default long shaderLoadingBudgetNanos() { return 0; }
    /** Schedules source-only work without running it inline. The task must never call GL.
     * May be called by the owner, compiler workers or storage completion threads, concurrently
     * with closeShaderPreparation(). Saturation/closure must reject, never execute on the caller. */
    default void executeShaderPreparation(Runnable task) { throw new UnsupportedOperationException("Shader workers are unavailable"); }
    /** Stops accepting source jobs without waiting for accepted work. Called at attachment teardown. */
    default void closeShaderPreparation() { }
    /** True only for an implemented KHR/ARB completion-query path on the current context. */
    default boolean supportsParallelShaderCompilation() { return false; }
    /** Poll completion before querying link status/reflection or using the program. */
    default boolean programCompilationComplete(int program) { throw new UnsupportedOperationException("Program polling is unavailable"); }
    /** Current GPU/driver/context, supported formats and adapter ABI identity, or null when
     * native binary persistence is unavailable. Query on the current owner context only. */
    default String programBinaryIdentity() { return null; }
    /** Enables retrieval before linking. Explicit owner-context loading only; may block. */
    default void hintProgramBinaryRetrievable(int program) { }
    /** Imports a supported binary on the owner during explicit loading. False means rejected;
     * check link status before use. Must not retain the borrowed bytes. May block. */
    default boolean restoreProgramBinary(int program, GLProgramBinary binary) { return false; }
    /** Exports at most maximumBytes during explicit owner-context loading, or returns null
     * when unavailable/oversized. The returned bytes are owned by the caller. May block. */
    default GLProgramBinary exportProgramBinary(int program, int maximumBytes) { return null; }
    /** Whether the adapter accepts GLSL 4.60 compute with SSBOs, images, and atomics. */
    default boolean supportsCompute() { return false; }
    default int createComputeProgram(String source) { throw new UnsupportedOperationException("Compute is unavailable"); }
    default void bindComputeBuffer(int slot, int buffer, int offset, int size, boolean uniform) {
        throw new UnsupportedOperationException("Compute buffers are unavailable");
    }
    default void bindStorageImage(int slot, int texture, TextureFormat format) {
        throw new UnsupportedOperationException("Storage images are unavailable");
    }
    default void dispatchCompute(int x, int y, int z) { throw new UnsupportedOperationException("Compute is unavailable"); }
    default void computeMemoryBarrier() { throw new UnsupportedOperationException("Compute is unavailable"); }
    default void copyBuffer(int source, int sourceOffset, int destination, int destinationOffset, int size) {
        throw new UnsupportedOperationException("Buffer copies are unavailable");
    }
    default void readBuffer(int source, int offset, ByteBuffer destination) {
        throw new UnsupportedOperationException("Buffer readback is unavailable");
    }
    /** Desktop MRT, per-target blend, four-sample attachments and explicit resolves. */
    default boolean supportsMultipleTargets() { return false; }

    default void texImageMultisample(int texture, TextureFormat format,
            int width, int height, int samples) {
        throw new UnsupportedOperationException("Multisample textures are unavailable");
    }

    /** Attaches a texture; index -1 denotes the depth attachment. */
    default void framebufferTexture(int index, int texture, int level, int samples) {
        throw new UnsupportedOperationException("Multiple targets are unavailable");
    }

    default void drawBuffers(int count) { throw new UnsupportedOperationException("MRT is unavailable"); }
    default void clearColorAttachment(int index, float red, float green, float blue, float alpha) {
        throw new UnsupportedOperationException("Indexed clears are unavailable");
    }
    default void resolveColorFramebuffer(int source, int index, int destination, int width, int height) {
        throw new UnsupportedOperationException("Color resolves are unavailable");
    }
    default void renderbufferStorageDepth(int width, int height, int samples) {
        if (samples != 1) throw new UnsupportedOperationException("Multisample depth is unavailable");
        renderbufferStorageDepth(width, height);
    }
    default void applyColorTargets(ColorTargetState[] targets) {
        if (targets.length != 1) throw new UnsupportedOperationException("Independent target state is unavailable");
    }
    /** Whether all portable raster, blend, depth/stencil and sample-mask state is implemented. */
    default boolean supportsCompletePipelineState() { return false; }

    /** Applies immutable state for the currently bound single-color render pipeline. */
    default void applyPipelineState(PrimitiveState primitive,
            ColorTargetState color,
            DepthStencilState depth,
            MultisampleState samples) {
        throw new UnsupportedOperationException("Complete pipeline state is unavailable");
    }

    /** Restores attachment write masks before render-pass clears. */
    default void resetAttachmentWriteMasks() { }
    /** Reports native context loss when supported. Queried on the owner at event/frame and
     * preparation boundaries and before cleanup. Adapters must retain an observed loss across
     * native restoration; the old resource domain cannot be reused. False does not promise
     * reset detection on adapters without this hook. */
    default boolean isContextLost() { return false; }

    /** True only when DEPTH32_FLOAT allocation and framebuffer attachment below are implemented. */
    default boolean supportsDepthTextures() { return false; }

    /** Allocates the bound single-sample DEPTH_COMPONENT32F texture, without source data. */
    default void texImageDepth32F(int width, int height) {
        throw new UnsupportedOperationException("Explicit depth textures are not supported by this GL API");
    }

    /** Attaches a DEPTH_COMPONENT32F texture to the bound offscreen framebuffer. */
    default void framebufferDepthTexture2D(int texture) {
        throw new UnsupportedOperationException("Explicit depth textures are not supported by this GL API");
    }

    /** Starts optional per-frame command and GPU diagnostics. */
    default void beginFrameMetrics(long frameId) {
    }

    /** Finishes optional per-frame command and GPU diagnostics. */
    default void endFrameMetrics() {
    }

    /** Releases optional diagnostics resources. */
    default void disposeFrameMetrics() {
    }

    /** Returns the latest diagnostics exposed by this implementation. */
    default GraphicsFrameMetrics frameMetrics() {
        return GraphicsFrameMetrics.UNAVAILABLE;
    }

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

    /** True when color mip allocation, uploads, attachment levels and independent filters are implemented. */
    default boolean supportsMipTextures() { return false; }

    /** True when RGBA16_FLOAT allocation, typed uploads, filtering and color attachments are implemented. */
    default boolean supportsRgba16FloatTextures() { return false; }

    /** Sized color storage enum for the common GL color formats. */
    static int colorInternalFormat(TextureFormat format) {
        return switch (format) {
            case RGBA8_UNORM -> 0x8058;
            case RGBA8_UNORM_SRGB -> 0x8C43;
            case RGBA16_FLOAT -> 0x881A;
            case R32_FLOAT -> 0x822E;
            default -> throw new FdxException("Unsupported GL color format: " + format);
        };
    }

    /** Transfer type enum, with native-order half-float components for RGBA16_FLOAT. */
    static int colorTransferType(TextureFormat format) {
        colorInternalFormat(format);
        return format == TextureFormat.RGBA16_FLOAT ? 0x140B
                : format == TextureFormat.R32_FLOAT ? 0x1406 : 0x1401;
    }

    /** Uploads native-order half-float RGBA or byte RGBA to an existing mip level. */
    default void texSubImage2D(TextureFormat format, int level,
            int width, int height, ByteBuffer data) {
        if (format != TextureFormat.RGBA8_UNORM
                && format != TextureFormat.RGBA8_UNORM_SRGB) {
            throw new FdxException("GL adapter does not implement typed texture uploads");
        }
        texSubImage2D(level, width, height, data);
    }

    default void texImage2D(TextureFormat format, int level, int width, int height, ByteBuffer data) {
        if (level != 0) throw new FdxException("GL adapter does not implement mip allocation");
        texImage2D(format, width, height, data);
    }

    default void texSubImage2D(int level, int width, int height, ByteBuffer data) {
        if (level != 0) throw new FdxException("GL adapter does not implement mip uploads");
        texSubImage2D(width, height, data);
    }

    default void textureMipRange2D(int levelCount) {
        if (levelCount != 1) throw new FdxException("GL adapter does not implement mip range");
    }

    default void textureFilters2D(TextureFilter min, TextureFilter mag, TextureMipmapFilter mip) {
        if (min != mag || mip != TextureMipmapFilter.NONE) {
            throw new FdxException("GL adapter does not implement independent texture filters");
        }
        textureFilter2D(min);
    }

    /** OpenGL minification enum shared by native adapters. */
    static int minificationFilter(TextureFilter min, TextureMipmapFilter mip) {
        return switch (mip) {
            case NONE -> min == TextureFilter.NEAREST ? 0x2600 : 0x2601;
            case NEAREST -> min == TextureFilter.NEAREST ? 0x2700 : 0x2701;
            case LINEAR -> min == TextureFilter.NEAREST ? 0x2702 : 0x2703;
        };
    }

    /** Allocates the requested RGBA8 storage; older adapters reject sRGB rather than mislabel raw bytes. */
    default void texImage2D(TextureFormat format, int width, int height, ByteBuffer data) {
        if (format != TextureFormat.RGBA8_UNORM) {
            throw new FdxException("GL adapter does not implement sRGB texture storage");
        }
        texImage2D(width, height, data);
    }

    /** Selects target encoding. GLES/WebGL encode sRGB attachments automatically; desktop GL must enable it. */
    default void framebufferSrgb(boolean enabled) {
        if (enabled) throw new FdxException("GL adapter does not implement sRGB target encoding");
    }

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

    default void framebufferTexture2D(int texture, int level) {
        if (level != 0) throw new FdxException("GL adapter does not implement mip attachments");
        framebufferTexture2D(texture);
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

    /** Disables color blending for opaque pipelines. */
    void disableAlphaBlending();

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
     * Runs the disable vertex attrib array step.
     *
     * @param index the index
     */
    void disableVertexAttribArray(int index);

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
