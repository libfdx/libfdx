package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;

import java.nio.ByteBuffer;

final class GLGraphicsDevice implements GraphicsDevice {
    private final ProviderId providerId;
    private final GLApi gl;

    GLGraphicsDevice(ProviderId providerId, GLApi gl) {
        this.providerId = providerId;
        this.gl = gl;
    }

    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("BufferDescriptor cannot be null");
        }
        if (descriptor.usage() != BufferUsage.VERTEX && descriptor.usage() != BufferUsage.INDEX) {
            throw new FdxException("GL currently supports vertex and index buffers only");
        }
        int buffer = gl.genBuffer();
        if (descriptor.usage() == BufferUsage.INDEX) {
            gl.bindElementArrayBuffer(buffer);
            gl.elementBufferData(descriptor.size());
            gl.bindElementArrayBuffer(0);
        } else {
            gl.bindArrayBuffer(buffer);
            gl.bufferData(descriptor.size());
            gl.bindArrayBuffer(0);
        }
        return new GLBufferHandle(providerId, gl, buffer, descriptor.size(), descriptor.usage());
    }

    @Override
    public void writeBuffer(Buffer buffer, ByteBuffer data) {
        if (buffer == null) {
            throw new FdxException("Buffer cannot be null");
        }
        if (data == null) {
            throw new FdxException("Buffer data cannot be null");
        }
        GLBufferHandle glBuffer = buffer.as();
        if (data.remaining() > glBuffer.size()) {
            throw new FdxException("Buffer data is larger than the destination buffer");
        }
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

    @Override
    public Texture createTexture(TextureDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("TextureDescriptor cannot be null");
        }
        if (descriptor.usage() != TextureUsage.SAMPLED) {
            throw new FdxException("GL currently supports sampled textures only");
        }
        if (descriptor.format() != TextureFormat.RGBA8_UNORM && descriptor.format() != TextureFormat.RGBA8_UNORM_SRGB) {
            throw new FdxException("GL currently supports RGBA8 textures only");
        }
        int texture = gl.genTexture();
        gl.bindTexture2D(texture);
        gl.texImage2D(descriptor.width(), descriptor.height(), null);
        gl.textureWrap2D(descriptor.wrapS(), descriptor.wrapT());
        gl.bindTexture2D(0);
        return new GLTextureHandle(providerId, gl, texture, descriptor.width(), descriptor.height(),
                descriptor.format(), descriptor.usage());
    }

    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        if (texture == null) {
            throw new FdxException("Texture cannot be null");
        }
        if (data == null) {
            throw new FdxException("Texture data cannot be null");
        }
        GLTextureHandle glTexture = texture.as();
        int expected = glTexture.width() * glTexture.height() * 4;
        if (data.remaining() < expected) {
            throw new FdxException("Texture data is smaller than the destination texture");
        }
        gl.bindTexture2D(glTexture.texture());
        gl.texSubImage2D(glTexture.width(), glTexture.height(), data);
        gl.bindTexture2D(0);
    }

    @Override
    public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        if (!descriptor.hasSource(ShaderLanguage.GLSL)) {
            throw new FdxException("GL currently supports GLSL shader modules only");
        }
        int vertexShader = compileShader(GLShaderType.VERTEX, descriptor.glslVertexSource(),
                descriptor.label() + " vertex");
        int fragmentShader = compileShader(GLShaderType.FRAGMENT, descriptor.glslFragmentSource(),
                descriptor.label() + " fragment");
        int program = gl.createProgram();
        gl.attachShader(program, vertexShader);
        gl.attachShader(program, fragmentShader);
        gl.linkProgram(program);
        gl.deleteShader(vertexShader);
        gl.deleteShader(fragmentShader);
        if (!gl.programLinkStatus(program)) {
            String log = gl.programInfoLog(program);
            gl.deleteProgram(program);
            throw new FdxException("Could not link GL shader module " + descriptor.label() + ": " + log);
        }
        return new GLShaderModuleHandle(providerId, gl, program);
    }

    @Override
    public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPipelineDescriptor cannot be null");
        }
        GLShaderModuleHandle shaderModule = descriptor.shaderModule().as();
        return new GLRenderPipelineHandle(providerId, shaderModule.program(), descriptor.primitiveTopology(),
                descriptor.vertexLayouts(), descriptor.sampledTextureCount(), descriptor.depthTestEnabled(),
                descriptor.depthWriteEnabled());
    }

    private int compileShader(GLShaderType type, String source, String label) {
        int shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.shaderCompileStatus(shader)) {
            String log = gl.shaderInfoLog(shader);
            gl.deleteShader(shader);
            throw new FdxException("Could not compile GL shader " + label + ": " + log);
        }
        return shader;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
