package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderModuleDescriptors;
import io.github.libfdx.graphics.ShaderTarget;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;

import java.nio.ByteBuffer;

/**
 * Represents a GL graphics device.
 *
 * @author xpenatan
 */
final class GLGraphicsDevice implements GraphicsDevice {
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final GLGraphicsAttachment attachment;

    GLGraphicsDevice(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain,
            GLGraphicsAttachment attachment) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.attachment = attachment;
    }

    /**
     * Creates a buffer.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("BufferDescriptor cannot be null");
        }
        if (descriptor.usage() != BufferUsage.VERTEX && descriptor.usage() != BufferUsage.INDEX) {
            throw new FdxException("GL currently supports vertex and index buffers only");
        }
        attachment.makeCurrent();
        int buffer = gl.genBuffer();
        try {
            if (descriptor.usage() == BufferUsage.INDEX) {
                gl.bindElementArrayBuffer(buffer);
                gl.elementBufferData(descriptor.size());
                gl.bindElementArrayBuffer(0);
            }
            else {
                gl.bindArrayBuffer(buffer);
                gl.bufferData(descriptor.size());
                gl.bindArrayBuffer(0);
            }
            return new GLBufferHandle(providerId, gl, resourceDomain, buffer, descriptor.size(), descriptor.usage());
        }
        catch (RuntimeException | Error failure) {
            rollbackBuffer(buffer, descriptor.usage(), failure);
            throw failure;
        }
    }

    /**
     * Runs the write buffer step.
     *
     * @param buffer the buffer
     * @param data the data
     */
    @Override
    public void writeBuffer(Buffer buffer, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Buffer data cannot be null");
        }
        GLBufferHandle glBuffer = GLResources.requireBuffer(buffer, resourceDomain, "Buffer");
        if (data.remaining() > glBuffer.size()) {
            throw new FdxException("Buffer data is larger than the destination buffer");
        }
        attachment.makeCurrent();
        if (glBuffer.usage() == BufferUsage.INDEX) {
            gl.bindElementArrayBuffer(glBuffer.buffer());
            gl.elementBufferSubData(data);
            gl.bindElementArrayBuffer(0);
        } else {
            gl.bindArrayBuffer(glBuffer.buffer());
            gl.bufferSubData(data);
            gl.bindArrayBuffer(0);
        }
    }

    /**
     * Creates a texture.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public Texture createTexture(TextureDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("TextureDescriptor cannot be null");
        }
        if (!descriptor.usage().sampled() && !descriptor.usage().renderAttachment()) {
            throw new FdxException("GL texture usage must allow sampling or render attachment binding");
        }
        if (descriptor.format() != TextureFormat.RGBA8_UNORM && descriptor.format() != TextureFormat.RGBA8_UNORM_SRGB) {
            throw new FdxException("GL currently supports RGBA8 textures only");
        }
        attachment.makeCurrent();
        int texture = gl.genTexture();
        try {
            gl.bindTexture2D(texture);
            gl.texImage2D(descriptor.width(), descriptor.height(), null);
            gl.textureFilter2D(descriptor.filter());
            gl.textureWrap2D(descriptor.wrapS(), descriptor.wrapT());
            gl.bindTexture2D(0);
            return new GLTextureHandle(providerId, gl, resourceDomain, texture, descriptor.width(),
                    descriptor.height(), descriptor.format(), descriptor.usage());
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, failure);
            throw failure;
        }
    }

    /**
     * Runs the write texture step.
     *
     * @param texture the texture
     * @param data the data
     */
    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Texture data cannot be null");
        }
        GLTextureHandle glTexture = GLResources.requireTexture(texture, resourceDomain, "Texture");
        int expected = glTexture.width() * glTexture.height() * 4;
        if (data.remaining() < expected) {
            throw new FdxException("Texture data is smaller than the destination texture");
        }
        attachment.makeCurrent();
        gl.bindTexture2D(glTexture.texture());
        gl.texSubImage2D(glTexture.width(), glTexture.height(), data);
        gl.bindTexture2D(0);
    }

    /**
     * Creates a shader module.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.forProvider(providerId), "GL");
        if (!descriptor.hasSource(ShaderLanguage.GLSL)) {
            throw new FdxException("GL currently supports GLSL shader modules only");
        }
        attachment.makeCurrent();
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GLShaderType.VERTEX, descriptor.glslVertexSource(),
                    descriptor.label() + " vertex");
            fragmentShader = compileShader(GLShaderType.FRAGMENT, descriptor.glslFragmentSource(),
                    descriptor.label() + " fragment");
            program = linkProgram(vertexShader, fragmentShader, descriptor.label());
            GLShaderModuleHandle handle = new GLShaderModuleHandle(providerId, gl, resourceDomain, program);
            gl.deleteShader(vertexShader);
            vertexShader = 0;
            gl.deleteShader(fragmentShader);
            fragmentShader = 0;
            return handle;
        }
        catch (RuntimeException | Error failure) {
            rollbackProgram(program, vertexShader, fragmentShader, failure);
            throw failure;
        }
    }

    /**
     * Creates a render pipeline.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPipelineDescriptor cannot be null");
        }
        GLShaderModuleHandle shaderModule = GLResources.requireShaderModule(descriptor.shaderModule(), resourceDomain,
                "Render pipeline shader module");
        attachment.makeCurrent();
        int pbrUniformBuffer = createPbrUniformBuffer(descriptor);
        try {
            return new GLRenderPipelineHandle(providerId, gl, resourceDomain, shaderModule,
                    descriptor.primitiveTopology(), descriptor.vertexLayouts(), descriptor.sampledTextureCount(),
                    descriptor.depthTestEnabled(), descriptor.depthWriteEnabled(), pbrUniformBuffer);
        }
        catch (RuntimeException | Error failure) {
            rollbackGeneratedBuffer(pbrUniformBuffer, failure);
            throw failure;
        }
    }

    int compileShader(GLShaderType type, String source, String label) {
        int shader = gl.createShader(type);
        try {
            gl.shaderSource(shader, normalizeGlslSource(source));
            gl.compileShader(shader);
            if (!gl.shaderCompileStatus(shader)) {
                String log = gl.shaderInfoLog(shader);
                throw new FdxException("Could not compile GL shader " + label + ": " + log);
            }
            return shader;
        }
        catch (RuntimeException | Error failure) {
            rollbackShader(shader, failure);
            throw failure;
        }
    }

    int linkProgram(int vertexShader, int fragmentShader, String label) {
        int program = gl.createProgram();
        try {
            gl.attachShader(program, vertexShader);
            gl.attachShader(program, fragmentShader);
            gl.linkProgram(program);
            if (!gl.programLinkStatus(program)) {
                String log = gl.programInfoLog(program);
                throw new FdxException("Could not link GL shader module " + label + ": " + log);
            }
            bindUniformBlock(program, "v_uniforms_block_ubo", 0);
            bindUniformBlock(program, "f_uniforms_block_ubo", 0);
            return program;
        }
        catch (RuntimeException | Error failure) {
            rollbackProgram(program, 0, 0, failure);
            throw failure;
        }
    }

    private int createPbrUniformBuffer(RenderPipelineDescriptor descriptor) {
        if (!usesPbrUniformBlock(descriptor)) {
            return 0;
        }
        int buffer = gl.genBuffer();
        try {
            gl.bindUniformBuffer(buffer);
            gl.uniformBufferData(GLRenderPipelineHandle.PBR_UNIFORM_BYTE_COUNT);
            gl.bindUniformBuffer(0);
            return buffer;
        }
        catch (RuntimeException | Error failure) {
            tryCleanupUniformBinding(failure);
            rollbackGeneratedBuffer(buffer, failure);
            throw failure;
        }
    }

    private void rollbackBuffer(int buffer, BufferUsage usage, Throwable failure) {
        try {
            if (usage == BufferUsage.INDEX) {
                gl.bindElementArrayBuffer(0);
            }
            else {
                gl.bindArrayBuffer(0);
            }
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        rollbackGeneratedBuffer(buffer, failure);
    }

    private void rollbackTexture(int texture, Throwable failure) {
        try {
            gl.bindTexture2D(0);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        if (texture == 0) {
            return;
        }
        try {
            gl.deleteTexture(texture);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void rollbackProgram(int program, int vertexShader, int fragmentShader, Throwable failure) {
        if (program != 0) {
            try {
                gl.deleteProgram(program);
            }
            catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        rollbackShader(vertexShader, failure);
        rollbackShader(fragmentShader, failure);
    }

    private void rollbackShader(int shader, Throwable failure) {
        if (shader == 0) {
            return;
        }
        try {
            gl.deleteShader(shader);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void tryCleanupUniformBinding(Throwable failure) {
        try {
            gl.bindUniformBuffer(0);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void rollbackGeneratedBuffer(int buffer, Throwable failure) {
        if (buffer == 0) {
            return;
        }
        try {
            gl.deleteBuffer(buffer);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void bindUniformBlock(int program, String name, int binding) {
        int blockIndex = gl.uniformBlockIndex(program, name);
        if (blockIndex >= 0) {
            gl.uniformBlockBinding(program, blockIndex, binding);
        }
    }

    private boolean usesPbrUniformBlock(RenderPipelineDescriptor descriptor) {
        ShaderBinding[] bindings = descriptor.shaderReflection().bindings();
        for (int i = 0; i < bindings.length; i++) {
            ShaderBinding binding = bindings[i];
            if ((binding.group() == 0 || binding.group() == 1)
                    && binding.binding() == 0
                    && binding.type() == ShaderBindingType.UNIFORM_BUFFER
                    && "uniforms".equals(binding.name())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeGlslSource(String source) {
        String actualSource = source != null ? source : "";
        actualSource = actualSource.replaceAll("layout\\(binding\\s*=\\s*[0-9]+\\s*,\\s*std140\\)", "layout(std140)");
        return actualSource.replaceAll(
                "gl_Position\\s*=\\s*vec4\\(\\s*([A-Za-z0-9_]+\\.position)\\.x\\s*,\\s*-\\(\\s*\\1\\.y\\s*\\)\\s*,\\s*\\(\\(2\\.0f?\\s*\\*\\s*\\1\\.z\\s*\\)\\s*-\\s*\\1\\.w\\s*\\)\\s*,\\s*\\1\\.w\\s*\\)\\s*;",
                "gl_Position = $1;");
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
