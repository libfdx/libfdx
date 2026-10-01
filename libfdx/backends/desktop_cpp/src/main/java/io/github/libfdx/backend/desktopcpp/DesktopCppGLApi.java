package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.StencilFaceState;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureMipmapFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;

import java.nio.ByteBuffer;

/**
 * Exposes API access for jNative GL.
 *
 * @author xpenatan
 */
final class DesktopCppGLApi implements GLApi {
    private final int[] nativePipelineState = new int[21];
    private final int[] nativeColors = new int[64];

    @Override
    public boolean supportsCompute() {
        return DesktopCppGLFeatures.supported(0);
    }

    @Override
    public boolean supportsMultipleTargets() {
        return DesktopCppGLFeatures.supported(1);
    }

    @Override
    public boolean supportsCompletePipelineState() {
        return DesktopCppGLFeatures.supported(2);
    }

    @Override
    public int createComputeProgram(String source) {
        int program = DesktopCppGLFeatures.computeProgram(source);
        if (program == 0)
            throw new io.github.libfdx.core.FdxException(
                    "Could not compile OpenGL compute program; see native stderr");
        return program;
    }

    @Override
    public void bindComputeBuffer(int slot, int buffer, int offset, int size, boolean uniform) {
        DesktopCppGLFeatures.computeBuffer(slot, buffer, offset, size, uniform);
    }

    @Override
    public void bindStorageImage(int slot, int texture, TextureFormat format) {
        DesktopCppGLFeatures.storageImage(slot, texture, GLApi.colorInternalFormat(format));
    }

    @Override
    public void dispatchCompute(int x, int y, int z) {
        DesktopCppGLFeatures.dispatch(x, y, z);
    }

    @Override
    public void computeMemoryBarrier() {
        DesktopCppGLFeatures.barrier();
    }

    @Override
    public void copyBuffer(
            int source, int sourceOffset, int destination, int destinationOffset, int size) {
        DesktopCppGLFeatures.copyBuffer(source, sourceOffset, destination, destinationOffset, size);
    }

    @Override
    public void readBuffer(int source, int offset, ByteBuffer output) {
        DesktopCppGLFeatures.readBuffer(source, offset, output, output.remaining());
    }

    @Override
    public void texImageMultisample(
            int texture, TextureFormat format, int width, int height, int samples) {
        int internal =
                format.isDepthStencil()
                        ? (format.hasStencil() ? 0x88F0 : 0x8CAC)
                        : GLApi.colorInternalFormat(format);
        DesktopCppGLFeatures.textureMultisample(texture, internal, width, height, samples);
    }

    @Override
    public void framebufferTexture(int index, int texture, int level, int samples) {
        DesktopCppGLFeatures.framebufferTexture(index, texture, level, samples);
    }

    @Override
    public void drawBuffers(int count) {
        DesktopCppGLFeatures.drawBuffers(count);
    }

    @Override
    public void clearColorAttachment(int index, float red, float green, float blue, float alpha) {
        DesktopCppGLFeatures.clearAttachment(index, red, green, blue, alpha);
    }

    @Override
    public void renderbufferStorageDepth(int width, int height, int samples) {
        DesktopCppGLFeatures.depthMultisample(width, height, samples);
    }

    @Override
    public void resolveColorFramebuffer(
            int source, int index, int destination, int width, int height) {
        DesktopCppGLFeatures.resolve(source, index, destination, width, height);
    }

    @Override
    public void resetAttachmentWriteMasks() {
        DesktopCppGLFeatures.resetMasks();
    }

    @Override
    public void applyPipelineState(
            PrimitiveState primitive,
            ColorTargetState color,
            DepthStencilState depth,
            MultisampleState samples) {
        int[] values = nativePipelineState;
        values[0] = primitive.cullMode().ordinal();
        values[1] = primitive.frontFace().ordinal();
        values[2] = samples.mask();
        values[3] = samples.alphaToCoverageEnabled() ? 1 : 0;
        values[4] = depth == null ? 0 : 1;
        values[5] = depth != null && depth.depthWriteEnabled() ? 1 : 0;
        values[7] = depth != null && depth.format().hasStencil() ? 1 : 0;
        values[10] = depth == null ? 0 : depth.depthBias();
        values[11] = Float.floatToRawIntBits(depth == null ? 0 : depth.depthBiasSlopeScale());
        values[12] = Float.floatToRawIntBits(depth == null ? 0 : depth.depthBiasClamp());
        if (depth != null) {
            values[6] = depth.depthCompare().ordinal();
            values[8] = depth.stencilReadMask();
            values[9] = depth.stencilWriteMask();
            writeStencil(values, 13, depth.stencilFront());
            writeStencil(values, 17, depth.stencilBack());
        }
        DesktopCppGLFeatures.pipelineState(values);
        writeColor(0, color);
        DesktopCppGLFeatures.colorTargets(nativeColors, 1);
    }

    @Override
    public void applyColorTargets(ColorTargetState[] targets) {
        if (targets.length > 8)
            throw new io.github.libfdx.core.FdxException("OpenGL target count exceeds eight");
        for (int i = 0; i < targets.length; i++) writeColor(i, targets[i]);
        DesktopCppGLFeatures.colorTargets(nativeColors, targets.length);
    }

    private static void writeStencil(int[] values, int offset, StencilFaceState face) {
        values[offset] = face.compare().ordinal();
        values[offset + 1] = face.fail().ordinal();
        values[offset + 2] = face.depthFail().ordinal();
        values[offset + 3] = face.pass().ordinal();
    }

    private void writeColor(int index, ColorTargetState color) {
        int offset = index * 8;
        nativeColors[offset] = color.blend() == null ? 0 : 1;
        nativeColors[offset + 1] = color.writeMask();
        if (color.blend() != null) {
            writeBlend(offset + 2, color.blend().color());
            writeBlend(offset + 5, color.blend().alpha());
        }
    }

    private void writeBlend(int offset, BlendComponent blend) {
        nativeColors[offset] = blend.sourceFactor().ordinal();
        nativeColors[offset + 1] = blend.destinationFactor().ordinal();
        nativeColors[offset + 2] = blend.operation().ordinal();
    }

    private DesktopCppAssetExecutor preparationExecutor;
    private boolean preparationClosed;
    private Boolean parallelCompilationSupported;

    @Override
    public int shaderPreparationWorkers() {
        return 2;
    }

    @Override
    public synchronized void executeShaderPreparation(Runnable task) {
        if (preparationClosed) throw new FdxException("GL shader preparation is closed");
        if (preparationExecutor == null) preparationExecutor = new DesktopCppAssetExecutor(2, 256);
        if (!preparationExecutor.submit(task))
            throw new FdxException("GL shader preparation queue is full");
    }

    @Override
    public synchronized void closeShaderPreparation() {
        preparationClosed = true;
        if (preparationExecutor != null) preparationExecutor.dispose();
    }

    @Override
    public boolean supportsParallelShaderCompilation() {
        if (parallelCompilationSupported == null) {
            parallelCompilationSupported = DesktopCppOpenGL.enableParallelShaderCompilation(2);
        }
        return parallelCompilationSupported;
    }

    @Override
    public boolean programCompilationComplete(int program) {
        return DesktopCppOpenGL.getProgramInt(program, 0x91B1) != 0;
    }

    @Override
    public boolean supportsMipTextures() {
        return true;
    }

    @Override
    public boolean supportsRgba16FloatTextures() {
        return true;
    }

    @Override
    public void textureMipRange2D(int levels) {
        DesktopCppOpenGL.glTexParameteri(DesktopCppOpenGL.TEXTURE_2D, 0x813C, 0);
        DesktopCppOpenGL.glTexParameteri(DesktopCppOpenGL.TEXTURE_2D, 0x813D, levels - 1);
    }

    @Override
    public void textureFilters2D(TextureFilter min, TextureFilter mag, TextureMipmapFilter mip) {
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D,
                DesktopCppOpenGL.TEXTURE_MIN_FILTER,
                GLApi.minificationFilter(min, mip));
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D, DesktopCppOpenGL.TEXTURE_MAG_FILTER, toNative(mag));
    }

    @Override
    public void framebufferTexture2D(int texture, int level) {
        DesktopCppOpenGL.glFramebufferTexture2D(
                DesktopCppOpenGL.FRAMEBUFFER,
                DesktopCppOpenGL.COLOR_ATTACHMENT0,
                DesktopCppOpenGL.TEXTURE_2D,
                texture,
                level);
    }

    @Override
    public boolean supportsDepthTextures() {
        return true;
    }

    @Override
    public void texImageDepth32F(int width, int height) {
        DesktopCppOpenGL.glTexImage2D(
                DesktopCppOpenGL.TEXTURE_2D, 0, 0x8CAC, width, height, 0, 0x1902, 0x1406, 0L);
    }

    @Override
    public void framebufferDepthTexture2D(int texture) {
        DesktopCppOpenGL.glFramebufferTexture2D(
                DesktopCppOpenGL.FRAMEBUFFER, 0x8D00, DesktopCppOpenGL.TEXTURE_2D, texture, 0);
    }

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return DesktopCppOpenGL.glCreateProgram();
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
            return DesktopCppOpenGL.glCreateShader(DesktopCppOpenGL.VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return DesktopCppOpenGL.glCreateShader(DesktopCppOpenGL.FRAGMENT_SHADER);
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
        DesktopCppOpenGL.glShaderSource(shader, source);
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        DesktopCppOpenGL.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return DesktopCppOpenGL.getShaderInt(shader, DesktopCppOpenGL.COMPILE_STATUS)
                != DesktopCppOpenGL.FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return DesktopCppOpenGL.getShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        DesktopCppOpenGL.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        DesktopCppOpenGL.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        DesktopCppOpenGL.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return DesktopCppOpenGL.getProgramInt(program, DesktopCppOpenGL.LINK_STATUS)
                != DesktopCppOpenGL.FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return DesktopCppOpenGL.getProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        DesktopCppOpenGL.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        DesktopCppOpenGL.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return DesktopCppOpenGL.genVertexArray();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        DesktopCppOpenGL.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        DesktopCppOpenGL.deleteVertexArray(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return DesktopCppOpenGL.genBuffer();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        DesktopCppOpenGL.glBindBuffer(DesktopCppOpenGL.ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        DesktopCppOpenGL.glBindBuffer(DesktopCppOpenGL.ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        DesktopCppOpenGL.glBufferData(
                DesktopCppOpenGL.ARRAY_BUFFER, size, 0L, DesktopCppOpenGL.DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        DesktopCppOpenGL.glBufferData(
                DesktopCppOpenGL.ELEMENT_ARRAY_BUFFER, size, 0L, DesktopCppOpenGL.STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        DesktopCppOpenGL.glBufferSubData(DesktopCppOpenGL.ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public boolean supportsBufferRangeInitialization() {
        return true;
    }

    @Override
    public void bufferSubData(int offset, ByteBuffer data) {
        DesktopCppOpenGL.glBufferSubData(
                DesktopCppOpenGL.ARRAY_BUFFER, offset, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        DesktopCppOpenGL.glBindBuffer(DesktopCppOpenGL.UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        DesktopCppOpenGL.glBufferData(
                DesktopCppOpenGL.UNIFORM_BUFFER, size, 0L, DesktopCppOpenGL.DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        DesktopCppOpenGL.glBufferSubData(
                DesktopCppOpenGL.UNIFORM_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        DesktopCppOpenGL.glBindBufferBase(DesktopCppOpenGL.UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        DesktopCppOpenGL.glBufferSubData(
                DesktopCppOpenGL.ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        DesktopCppOpenGL.deleteBuffer(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return DesktopCppOpenGL.genTexture();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        DesktopCppOpenGL.glBindTexture(DesktopCppOpenGL.TEXTURE_2D, texture);
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
        texImage2D(io.github.libfdx.graphics.TextureFormat.RGBA8_UNORM, width, height, data);
    }

    @Override
    public void framebufferSrgb(boolean enabled) {
        if (enabled) DesktopCppOpenGL.glEnable(0x8DB9);
        else DesktopCppOpenGL.glDisable(0x8DB9);
    }

    @Override
    public void texImage2D(
            io.github.libfdx.graphics.TextureFormat format,
            int width,
            int height,
            ByteBuffer data) {
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D,
                DesktopCppOpenGL.TEXTURE_MIN_FILTER,
                DesktopCppOpenGL.LINEAR);
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D,
                DesktopCppOpenGL.TEXTURE_MAG_FILTER,
                DesktopCppOpenGL.LINEAR);
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D,
                DesktopCppOpenGL.TEXTURE_WRAP_S,
                DesktopCppOpenGL.CLAMP_TO_EDGE);
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D,
                DesktopCppOpenGL.TEXTURE_WRAP_T,
                DesktopCppOpenGL.CLAMP_TO_EDGE);
        allocateTextureLevel(format, 0, width, height, data);
    }

    @Override
    public void texImage2D(
            TextureFormat format, int level, int width, int height, ByteBuffer data) {
        if (level == 0) texImage2D(format, width, height, data);
        else allocateTextureLevel(format, level, width, height, data);
    }

    private void allocateTextureLevel(
            TextureFormat format, int level, int width, int height, ByteBuffer data) {
        DesktopCppOpenGL.glTexImage2D(
                DesktopCppOpenGL.TEXTURE_2D,
                level,
                GLApi.colorInternalFormat(format),
                width,
                height,
                0,
                format == TextureFormat.R32_FLOAT ? 0x1903 : DesktopCppOpenGL.RGBA,
                GLApi.colorTransferType(format),
                0L);
        if (data != null) {
            texSubImage2D(format, level, width, height, data);
        }
    }

    @Override
    public void texSubImage2D(
            TextureFormat format, int level, int width, int height, ByteBuffer data) {
        DesktopCppOpenGL.glTexSubImage2D(
                DesktopCppOpenGL.TEXTURE_2D,
                level,
                0,
                0,
                width,
                height,
                format == TextureFormat.R32_FLOAT ? 0x1903 : DesktopCppOpenGL.RGBA,
                GLApi.colorTransferType(format),
                data);
    }

    @Override
    public void texSubImage2D(int level, int width, int height, ByteBuffer data) {
        texSubImage2D(TextureFormat.RGBA8_UNORM, level, width, height, data);
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
        DesktopCppOpenGL.glTexSubImage2D(
                DesktopCppOpenGL.TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                DesktopCppOpenGL.RGBA,
                DesktopCppOpenGL.UNSIGNED_BYTE,
                data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D, DesktopCppOpenGL.TEXTURE_WRAP_S, toNative(wrapS));
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D, DesktopCppOpenGL.TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D, DesktopCppOpenGL.TEXTURE_MIN_FILTER, nativeFilter);
        DesktopCppOpenGL.glTexParameteri(
                DesktopCppOpenGL.TEXTURE_2D, DesktopCppOpenGL.TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        DesktopCppOpenGL.deleteTexture(texture);
    }

    /**
     * Returns a new framebuffer handle.
     *
     * @return the framebuffer handle
     */
    @Override
    public int genFramebuffer() {
        return DesktopCppOpenGL.genFramebuffer();
    }

    /**
     * Binds the framebuffer, or the default framebuffer for zero.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void bindFramebuffer(int framebuffer) {
        DesktopCppOpenGL.glBindFramebuffer(DesktopCppOpenGL.FRAMEBUFFER, framebuffer);
    }

    /**
     * Attaches a 2D texture to the current framebuffer color attachment.
     *
     * @param texture the texture
     */
    @Override
    public void framebufferTexture2D(int texture) {
        DesktopCppOpenGL.glFramebufferTexture2D(
                DesktopCppOpenGL.FRAMEBUFFER,
                DesktopCppOpenGL.COLOR_ATTACHMENT0,
                DesktopCppOpenGL.TEXTURE_2D,
                texture,
                0);
    }

    /**
     * Returns whether the current framebuffer is complete.
     *
     * @return true when complete
     */
    @Override
    public boolean framebufferComplete() {
        return DesktopCppOpenGL.glCheckFramebufferStatus(DesktopCppOpenGL.FRAMEBUFFER)
                == DesktopCppOpenGL.FRAMEBUFFER_COMPLETE;
    }

    /**
     * Deletes a framebuffer handle.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void deleteFramebuffer(int framebuffer) {
        DesktopCppOpenGL.deleteFramebuffer(framebuffer);
    }

    /**
     * Returns a new renderbuffer handle.
     *
     * @return the renderbuffer handle
     */
    @Override
    public int genRenderbuffer() {
        return DesktopCppOpenGL.genRenderbuffer();
    }

    /**
     * Binds a renderbuffer.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void bindRenderbuffer(int renderbuffer) {
        DesktopCppOpenGL.glBindRenderbuffer(DesktopCppOpenGL.RENDERBUFFER, renderbuffer);
    }

    /**
     * Allocates 24-bit depth storage for the current renderbuffer.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void renderbufferStorageDepth(int width, int height) {
        DesktopCppOpenGL.glRenderbufferStorage(
                DesktopCppOpenGL.RENDERBUFFER, DesktopCppOpenGL.DEPTH_COMPONENT24, width, height);
    }

    /**
     * Attaches a depth renderbuffer to the current framebuffer.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void framebufferRenderbufferDepth(int renderbuffer) {
        DesktopCppOpenGL.glFramebufferRenderbuffer(
                DesktopCppOpenGL.FRAMEBUFFER,
                DesktopCppOpenGL.DEPTH_ATTACHMENT,
                DesktopCppOpenGL.RENDERBUFFER,
                renderbuffer);
    }

    /**
     * Deletes a renderbuffer handle.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        DesktopCppOpenGL.deleteRenderbuffer(renderbuffer);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        DesktopCppOpenGL.glActiveTexture(DesktopCppOpenGL.TEXTURE0 + slot);
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
        return DesktopCppOpenGL.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        DesktopCppOpenGL.glUniform1i(location, value);
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
        return DesktopCppOpenGL.glGetUniformBlockIndex(program, name);
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
        DesktopCppOpenGL.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        DesktopCppOpenGL.glUniform1f(location, value);
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
        DesktopCppOpenGL.glUniform3f(location, x, y, z);
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
        DesktopCppOpenGL.glUniform4f(location, x, y, z, w);
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
        DesktopCppOpenGL.glUniformMatrix4fv(location, 1, transpose, values);
    }

    /** Runs the enable alpha blending step. */
    @Override
    public void enableAlphaBlending() {
        DesktopCppOpenGL.glEnable(DesktopCppOpenGL.BLEND);
        DesktopCppOpenGL.glBlendFuncSeparate(
                DesktopCppOpenGL.SRC_ALPHA,
                DesktopCppOpenGL.ONE_MINUS_SRC_ALPHA,
                DesktopCppOpenGL.ONE,
                DesktopCppOpenGL.ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void disableAlphaBlending() {
        DesktopCppOpenGL.glDisable(DesktopCppOpenGL.BLEND);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            DesktopCppOpenGL.glEnable(DesktopCppOpenGL.DEPTH_TEST);
        } else {
            DesktopCppOpenGL.glDisable(DesktopCppOpenGL.DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        DesktopCppOpenGL.glDepthMask(enabled);
    }

    /** Runs the depth func less equal step. */
    @Override
    public void depthFuncLessEqual() {
        DesktopCppOpenGL.glDepthFunc(DesktopCppOpenGL.LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        DesktopCppOpenGL.glEnableVertexAttribArray(index);
    }

    /**
     * Runs the disable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void disableVertexAttribArray(int index) {
        DesktopCppOpenGL.glDisableVertexAttribArray(index);
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
        DesktopCppOpenGL.glVertexAttribPointer(
                index, size, DesktopCppOpenGL.FLOAT, false, stride, offset);
    }

    /**
     * Configures a vertex attribute with its declared storage format.
     *
     * @param index the index
     * @param format the format
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, VertexFormat format, int stride, int offset) {
        int nativeType =
                format == VertexFormat.UNORM8X4
                        ? DesktopCppOpenGL.UNSIGNED_BYTE
                        : DesktopCppOpenGL.FLOAT;
        boolean normalized = format == VertexFormat.UNORM8X4;
        DesktopCppOpenGL.glVertexAttribPointer(
                index, format.componentCount(), nativeType, normalized, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        DesktopCppOpenGL.glVertexAttribDivisor(index, divisor);
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
        DesktopCppOpenGL.glViewport(x, y, width, height);
    }

    /**
     * Enables or disables scissor testing.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            DesktopCppOpenGL.glEnable(DesktopCppOpenGL.SCISSOR_TEST);
        } else {
            DesktopCppOpenGL.glDisable(DesktopCppOpenGL.SCISSOR_TEST);
        }
    }

    /**
     * Sets the scissor rectangle.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void scissor(int x, int y, int width, int height) {
        DesktopCppOpenGL.glScissor(x, y, width, height);
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
        DesktopCppOpenGL.glClearColor(red, green, blue, alpha);
    }

    /** Runs the clear color buffer step. */
    @Override
    public void clearColorBuffer() {
        DesktopCppOpenGL.glClear(DesktopCppOpenGL.COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        DesktopCppOpenGL.glClearDepth(depth);
    }

    /** Runs the clear depth buffer step. */
    @Override
    public void clearDepthBuffer() {
        DesktopCppOpenGL.glClear(DesktopCppOpenGL.DEPTH_BUFFER_BIT);
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
        DesktopCppOpenGL.glDrawArrays(toNative(topology), firstVertex, vertexCount);
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
    public void drawArraysInstanced(
            PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        DesktopCppOpenGL.glDrawArraysInstanced(
                toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    /**
     * Reads the current back buffer as tightly packed RGBA8 pixels.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the pixel buffer
     */
    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        if (width <= 0 || height <= 0) {
            return ByteBuffer.allocateDirect(0);
        }
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount);
        DesktopCppOpenGL.glPixelStorei(DesktopCppOpenGL.PACK_ALIGNMENT, 1);
        DesktopCppOpenGL.glReadBuffer(DesktopCppOpenGL.BACK);
        DesktopCppOpenGL.glReadPixels(
                0, 0, width, height, DesktopCppOpenGL.RGBA, DesktopCppOpenGL.UNSIGNED_BYTE, pixels);
        pixels.position(0);
        pixels.limit(byteCount);
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
        DesktopCppOpenGL.glDrawElements(
                toNative(topology), indexCount, DesktopCppOpenGL.UNSIGNED_SHORT, offsetBytes);
    }

    /**
     * Draws indexed elements with a base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsBaseVertex(
            PrimitiveTopology topology, int indexCount, int offsetBytes, int baseVertex) {
        DesktopCppOpenGL.glDrawElementsBaseVertex(
                toNative(topology),
                indexCount,
                DesktopCppOpenGL.UNSIGNED_SHORT,
                offsetBytes,
                baseVertex);
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
    public void drawElementsInstanced(
            PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        DesktopCppOpenGL.glDrawElementsInstanced(
                toNative(topology),
                indexCount,
                DesktopCppOpenGL.UNSIGNED_SHORT,
                offsetBytes,
                instanceCount);
    }

    /**
     * Draws instanced indexed elements with a base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsInstancedBaseVertex(
            PrimitiveTopology topology,
            int indexCount,
            int offsetBytes,
            int instanceCount,
            int baseVertex) {
        DesktopCppOpenGL.glDrawElementsInstancedBaseVertex(
                toNative(topology),
                indexCount,
                DesktopCppOpenGL.UNSIGNED_SHORT,
                offsetBytes,
                instanceCount,
                baseVertex);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return DesktopCppOpenGL.LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return DesktopCppOpenGL.TRIANGLE_STRIP;
        }
        return DesktopCppOpenGL.TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return DesktopCppOpenGL.REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return DesktopCppOpenGL.MIRRORED_REPEAT;
        }
        return DesktopCppOpenGL.CLAMP_TO_EDGE;
    }

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? DesktopCppOpenGL.NEAREST : DesktopCppOpenGL.LINEAR;
    }
}
