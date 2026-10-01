package io.github.libfdx.backend.desktopc;

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
 * Exposes API access for desktop C GL.
 *
 * @author xpenatan
 */
final class DesktopCGLApi implements GLApi {
    private final int[] nativePipelineState = new int[21];
    private final int[] nativeColors = new int[64];

    @Override
    public boolean supportsCompute() {
        return DesktopCGLFeatures.supported(0);
    }

    @Override
    public boolean supportsMultipleTargets() {
        return DesktopCGLFeatures.supported(1);
    }

    @Override
    public boolean supportsCompletePipelineState() {
        return DesktopCGLFeatures.supported(2);
    }

    @Override
    public int createComputeProgram(String source) {
        int program = DesktopCGLFeatures.computeProgram(source);
        if (program == 0)
            throw new io.github.libfdx.core.FdxException(
                    "Could not compile OpenGL compute program; see native stderr");
        return program;
    }

    @Override
    public void bindComputeBuffer(int slot, int buffer, int offset, int size, boolean uniform) {
        DesktopCGLFeatures.computeBuffer(slot, buffer, offset, size, uniform);
    }

    @Override
    public void bindStorageImage(int slot, int texture, TextureFormat format) {
        DesktopCGLFeatures.storageImage(slot, texture, GLApi.colorInternalFormat(format));
    }

    @Override
    public void dispatchCompute(int x, int y, int z) {
        DesktopCGLFeatures.dispatch(x, y, z);
    }

    @Override
    public void computeMemoryBarrier() {
        DesktopCGLFeatures.barrier();
    }

    @Override
    public void copyBuffer(
            int source, int sourceOffset, int destination, int destinationOffset, int size) {
        DesktopCGLFeatures.copyBuffer(source, sourceOffset, destination, destinationOffset, size);
    }

    @Override
    public void readBuffer(int source, int offset, ByteBuffer output) {
        DesktopCGLFeatures.readBuffer(source, offset, output, output.remaining());
    }

    @Override
    public void texImageMultisample(
            int texture, TextureFormat format, int width, int height, int samples) {
        int internal =
                format.isDepthStencil()
                        ? (format.hasStencil() ? 0x88F0 : 0x8CAC)
                        : GLApi.colorInternalFormat(format);
        DesktopCGLFeatures.textureMultisample(texture, internal, width, height, samples);
    }

    @Override
    public void framebufferTexture(int index, int texture, int level, int samples) {
        DesktopCGLFeatures.framebufferTexture(index, texture, level, samples);
    }

    @Override
    public void drawBuffers(int count) {
        DesktopCGLFeatures.drawBuffers(count);
    }

    @Override
    public void clearColorAttachment(int index, float red, float green, float blue, float alpha) {
        DesktopCGLFeatures.clearAttachment(index, red, green, blue, alpha);
    }

    @Override
    public void renderbufferStorageDepth(int width, int height, int samples) {
        DesktopCGLFeatures.depthMultisample(width, height, samples);
    }

    @Override
    public void resolveColorFramebuffer(
            int source, int index, int destination, int width, int height) {
        DesktopCGLFeatures.resolve(source, index, destination, width, height);
    }

    @Override
    public void resetAttachmentWriteMasks() {
        DesktopCGLFeatures.resetMasks();
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
        DesktopCGLFeatures.pipelineState(values);
        writeColor(0, color);
        DesktopCGLFeatures.colorTargets(nativeColors, 1);
    }

    @Override
    public void applyColorTargets(ColorTargetState[] targets) {
        if (targets.length > 8)
            throw new io.github.libfdx.core.FdxException("OpenGL target count exceeds eight");
        for (int i = 0; i < targets.length; i++) writeColor(i, targets[i]);
        DesktopCGLFeatures.colorTargets(nativeColors, targets.length);
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

    private DesktopCAssetExecutor preparationExecutor;
    private boolean preparationClosed;
    private Boolean parallelCompilationSupported;

    @Override
    public int shaderPreparationWorkers() {
        return 2;
    }

    @Override
    public synchronized void executeShaderPreparation(Runnable task) {
        if (preparationClosed) throw new FdxException("GL shader preparation is closed");
        if (preparationExecutor == null) preparationExecutor = new DesktopCAssetExecutor(2, 256);
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
            parallelCompilationSupported = DesktopCOpenGL.enableParallelShaderCompilation(2);
        }
        return parallelCompilationSupported;
    }

    @Override
    public boolean programCompilationComplete(int program) {
        return DesktopCOpenGL.getProgramInt(program, 0x91B1) != 0;
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
        DesktopCOpenGL.glTexParameteri(DesktopCOpenGL.TEXTURE_2D, 0x813C, 0);
        DesktopCOpenGL.glTexParameteri(DesktopCOpenGL.TEXTURE_2D, 0x813D, levels - 1);
    }

    @Override
    public void textureFilters2D(TextureFilter min, TextureFilter mag, TextureMipmapFilter mip) {
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D,
                DesktopCOpenGL.TEXTURE_MIN_FILTER,
                GLApi.minificationFilter(min, mip));
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D, DesktopCOpenGL.TEXTURE_MAG_FILTER, toNative(mag));
    }

    @Override
    public void framebufferTexture2D(int texture, int level) {
        DesktopCOpenGL.glFramebufferTexture2D(
                DesktopCOpenGL.FRAMEBUFFER,
                DesktopCOpenGL.COLOR_ATTACHMENT0,
                DesktopCOpenGL.TEXTURE_2D,
                texture,
                level);
    }

    @Override
    public boolean supportsDepthTextures() {
        return true;
    }

    @Override
    public void texImageDepth32F(int width, int height) {
        DesktopCOpenGL.glTexImage2D(
                DesktopCOpenGL.TEXTURE_2D, 0, 0x8CAC, width, height, 0, 0x1902, 0x1406, 0L);
    }

    @Override
    public void framebufferDepthTexture2D(int texture) {
        DesktopCOpenGL.glFramebufferTexture2D(
                DesktopCOpenGL.FRAMEBUFFER, 0x8D00, DesktopCOpenGL.TEXTURE_2D, texture, 0);
    }

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return DesktopCOpenGL.glCreateProgram();
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
            return DesktopCOpenGL.glCreateShader(DesktopCOpenGL.VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return DesktopCOpenGL.glCreateShader(DesktopCOpenGL.FRAGMENT_SHADER);
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
        DesktopCOpenGL.glShaderSource(shader, source);
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        DesktopCOpenGL.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return DesktopCOpenGL.getShaderInt(shader, DesktopCOpenGL.COMPILE_STATUS)
                != DesktopCOpenGL.FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return DesktopCOpenGL.getShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        DesktopCOpenGL.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        DesktopCOpenGL.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        DesktopCOpenGL.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return DesktopCOpenGL.getProgramInt(program, DesktopCOpenGL.LINK_STATUS)
                != DesktopCOpenGL.FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return DesktopCOpenGL.getProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        DesktopCOpenGL.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        DesktopCOpenGL.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return DesktopCOpenGL.genVertexArray();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        DesktopCOpenGL.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        DesktopCOpenGL.deleteVertexArray(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return DesktopCOpenGL.genBuffer();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        DesktopCOpenGL.glBindBuffer(DesktopCOpenGL.ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        DesktopCOpenGL.glBindBuffer(DesktopCOpenGL.ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        DesktopCOpenGL.glBufferData(
                DesktopCOpenGL.ARRAY_BUFFER, size, 0L, DesktopCOpenGL.DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        DesktopCOpenGL.glBufferData(
                DesktopCOpenGL.ELEMENT_ARRAY_BUFFER, size, 0L, DesktopCOpenGL.STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        DesktopCOpenGL.glBufferSubData(DesktopCOpenGL.ARRAY_BUFFER, 0, data.remaining(), data);
    }

    @Override
    public boolean supportsBufferRangeInitialization() {
        return true;
    }

    @Override
    public void bufferSubData(int offset, ByteBuffer data) {
        DesktopCOpenGL.glBufferSubData(
                DesktopCOpenGL.ARRAY_BUFFER, offset, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        DesktopCOpenGL.glBindBuffer(DesktopCOpenGL.UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        DesktopCOpenGL.glBufferData(
                DesktopCOpenGL.UNIFORM_BUFFER, size, 0L, DesktopCOpenGL.DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        DesktopCOpenGL.glBufferSubData(
                DesktopCOpenGL.UNIFORM_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        DesktopCOpenGL.glBindBufferBase(DesktopCOpenGL.UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        DesktopCOpenGL.glBufferSubData(
                DesktopCOpenGL.ELEMENT_ARRAY_BUFFER, 0, data.remaining(), data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        DesktopCOpenGL.deleteBuffer(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return DesktopCOpenGL.genTexture();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        DesktopCOpenGL.glBindTexture(DesktopCOpenGL.TEXTURE_2D, texture);
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
        if (enabled) DesktopCOpenGL.glEnable(0x8DB9);
        else DesktopCOpenGL.glDisable(0x8DB9);
    }

    @Override
    public void texImage2D(
            io.github.libfdx.graphics.TextureFormat format,
            int width,
            int height,
            ByteBuffer data) {
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D,
                DesktopCOpenGL.TEXTURE_MIN_FILTER,
                DesktopCOpenGL.LINEAR);
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D,
                DesktopCOpenGL.TEXTURE_MAG_FILTER,
                DesktopCOpenGL.LINEAR);
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D,
                DesktopCOpenGL.TEXTURE_WRAP_S,
                DesktopCOpenGL.CLAMP_TO_EDGE);
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D,
                DesktopCOpenGL.TEXTURE_WRAP_T,
                DesktopCOpenGL.CLAMP_TO_EDGE);
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
        DesktopCOpenGL.glTexImage2D(
                DesktopCOpenGL.TEXTURE_2D,
                level,
                GLApi.colorInternalFormat(format),
                width,
                height,
                0,
                format == TextureFormat.R32_FLOAT ? 0x1903 : DesktopCOpenGL.RGBA,
                GLApi.colorTransferType(format),
                0L);
        if (data != null) {
            texSubImage2D(format, level, width, height, data);
        }
    }

    @Override
    public void texSubImage2D(
            TextureFormat format, int level, int width, int height, ByteBuffer data) {
        DesktopCOpenGL.glTexSubImage2D(
                DesktopCOpenGL.TEXTURE_2D,
                level,
                0,
                0,
                width,
                height,
                format == TextureFormat.R32_FLOAT ? 0x1903 : DesktopCOpenGL.RGBA,
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
        DesktopCOpenGL.glTexSubImage2D(
                DesktopCOpenGL.TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                DesktopCOpenGL.RGBA,
                DesktopCOpenGL.UNSIGNED_BYTE,
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
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D, DesktopCOpenGL.TEXTURE_WRAP_S, toNative(wrapS));
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D, DesktopCOpenGL.TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D, DesktopCOpenGL.TEXTURE_MIN_FILTER, nativeFilter);
        DesktopCOpenGL.glTexParameteri(
                DesktopCOpenGL.TEXTURE_2D, DesktopCOpenGL.TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        DesktopCOpenGL.deleteTexture(texture);
    }

    /**
     * Returns a new framebuffer handle.
     *
     * @return the framebuffer handle
     */
    @Override
    public int genFramebuffer() {
        return DesktopCOpenGL.genFramebuffer();
    }

    /**
     * Binds the framebuffer, or the default framebuffer for zero.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void bindFramebuffer(int framebuffer) {
        DesktopCOpenGL.glBindFramebuffer(DesktopCOpenGL.FRAMEBUFFER, framebuffer);
    }

    /**
     * Attaches a 2D texture to the current framebuffer color attachment.
     *
     * @param texture the texture
     */
    @Override
    public void framebufferTexture2D(int texture) {
        DesktopCOpenGL.glFramebufferTexture2D(
                DesktopCOpenGL.FRAMEBUFFER,
                DesktopCOpenGL.COLOR_ATTACHMENT0,
                DesktopCOpenGL.TEXTURE_2D,
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
        return DesktopCOpenGL.glCheckFramebufferStatus(DesktopCOpenGL.FRAMEBUFFER)
                == DesktopCOpenGL.FRAMEBUFFER_COMPLETE;
    }

    /**
     * Deletes a framebuffer handle.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void deleteFramebuffer(int framebuffer) {
        DesktopCOpenGL.deleteFramebuffer(framebuffer);
    }

    /**
     * Returns a new renderbuffer handle.
     *
     * @return the renderbuffer handle
     */
    @Override
    public int genRenderbuffer() {
        return DesktopCOpenGL.genRenderbuffer();
    }

    /**
     * Binds a renderbuffer.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void bindRenderbuffer(int renderbuffer) {
        DesktopCOpenGL.glBindRenderbuffer(DesktopCOpenGL.RENDERBUFFER, renderbuffer);
    }

    /**
     * Allocates 24-bit depth storage for the current renderbuffer.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void renderbufferStorageDepth(int width, int height) {
        DesktopCOpenGL.glRenderbufferStorage(
                DesktopCOpenGL.RENDERBUFFER, DesktopCOpenGL.DEPTH_COMPONENT24, width, height);
    }

    /**
     * Attaches a depth renderbuffer to the current framebuffer.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void framebufferRenderbufferDepth(int renderbuffer) {
        DesktopCOpenGL.glFramebufferRenderbuffer(
                DesktopCOpenGL.FRAMEBUFFER,
                DesktopCOpenGL.DEPTH_ATTACHMENT,
                DesktopCOpenGL.RENDERBUFFER,
                renderbuffer);
    }

    /**
     * Deletes a renderbuffer handle.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        DesktopCOpenGL.deleteRenderbuffer(renderbuffer);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        DesktopCOpenGL.glActiveTexture(DesktopCOpenGL.TEXTURE0 + slot);
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
        return DesktopCOpenGL.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        DesktopCOpenGL.glUniform1i(location, value);
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
        return DesktopCOpenGL.glGetUniformBlockIndex(program, name);
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
        DesktopCOpenGL.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        DesktopCOpenGL.glUniform1f(location, value);
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
        DesktopCOpenGL.glUniform3f(location, x, y, z);
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
        DesktopCOpenGL.glUniform4f(location, x, y, z, w);
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
        DesktopCOpenGL.glUniformMatrix4fv(location, 1, transpose, values);
    }

    /** Runs the enable alpha blending step. */
    @Override
    public void enableAlphaBlending() {
        DesktopCOpenGL.glEnable(DesktopCOpenGL.BLEND);
        DesktopCOpenGL.glBlendFuncSeparate(
                DesktopCOpenGL.SRC_ALPHA,
                DesktopCOpenGL.ONE_MINUS_SRC_ALPHA,
                DesktopCOpenGL.ONE,
                DesktopCOpenGL.ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void disableAlphaBlending() {
        DesktopCOpenGL.glDisable(DesktopCOpenGL.BLEND);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            DesktopCOpenGL.glEnable(DesktopCOpenGL.DEPTH_TEST);
        } else {
            DesktopCOpenGL.glDisable(DesktopCOpenGL.DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        DesktopCOpenGL.glDepthMask(enabled);
    }

    /** Runs the depth func less equal step. */
    @Override
    public void depthFuncLessEqual() {
        DesktopCOpenGL.glDepthFunc(DesktopCOpenGL.LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        DesktopCOpenGL.glEnableVertexAttribArray(index);
    }

    /**
     * Runs the disable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void disableVertexAttribArray(int index) {
        DesktopCOpenGL.glDisableVertexAttribArray(index);
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
        DesktopCOpenGL.glVertexAttribPointer(
                index, size, DesktopCOpenGL.FLOAT, false, stride, offset);
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
                        ? DesktopCOpenGL.UNSIGNED_BYTE
                        : DesktopCOpenGL.FLOAT;
        boolean normalized = format == VertexFormat.UNORM8X4;
        DesktopCOpenGL.glVertexAttribPointer(
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
        DesktopCOpenGL.glVertexAttribDivisor(index, divisor);
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
        DesktopCOpenGL.glViewport(x, y, width, height);
    }

    /**
     * Enables or disables scissor testing.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            DesktopCOpenGL.glEnable(DesktopCOpenGL.SCISSOR_TEST);
        } else {
            DesktopCOpenGL.glDisable(DesktopCOpenGL.SCISSOR_TEST);
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
        DesktopCOpenGL.glScissor(x, y, width, height);
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
        DesktopCOpenGL.glClearColor(red, green, blue, alpha);
    }

    /** Runs the clear color buffer step. */
    @Override
    public void clearColorBuffer() {
        DesktopCOpenGL.glClear(DesktopCOpenGL.COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        DesktopCOpenGL.glClearDepth(depth);
    }

    /** Runs the clear depth buffer step. */
    @Override
    public void clearDepthBuffer() {
        DesktopCOpenGL.glClear(DesktopCOpenGL.DEPTH_BUFFER_BIT);
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
        DesktopCOpenGL.glDrawArrays(toNative(topology), firstVertex, vertexCount);
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
        DesktopCOpenGL.glDrawArraysInstanced(
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
        DesktopCOpenGL.glPixelStorei(DesktopCOpenGL.PACK_ALIGNMENT, 1);
        DesktopCOpenGL.glReadBuffer(DesktopCOpenGL.BACK);
        DesktopCOpenGL.glReadPixels(
                0, 0, width, height, DesktopCOpenGL.RGBA, DesktopCOpenGL.UNSIGNED_BYTE, pixels);
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
        DesktopCOpenGL.glDrawElements(
                toNative(topology), indexCount, DesktopCOpenGL.UNSIGNED_SHORT, offsetBytes);
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
        DesktopCOpenGL.glDrawElementsBaseVertex(
                toNative(topology),
                indexCount,
                DesktopCOpenGL.UNSIGNED_SHORT,
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
        DesktopCOpenGL.glDrawElementsInstanced(
                toNative(topology),
                indexCount,
                DesktopCOpenGL.UNSIGNED_SHORT,
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
        DesktopCOpenGL.glDrawElementsInstancedBaseVertex(
                toNative(topology),
                indexCount,
                DesktopCOpenGL.UNSIGNED_SHORT,
                offsetBytes,
                instanceCount,
                baseVertex);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return DesktopCOpenGL.LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return DesktopCOpenGL.TRIANGLE_STRIP;
        }
        return DesktopCOpenGL.TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return DesktopCOpenGL.REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return DesktopCOpenGL.MIRRORED_REPEAT;
        }
        return DesktopCOpenGL.CLAMP_TO_EDGE;
    }

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? DesktopCOpenGL.NEAREST : DesktopCOpenGL.LINEAR;
    }
}
