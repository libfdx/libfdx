package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

import java.nio.ByteBuffer;

public interface GraphicsDevice extends ProviderHandle {
    Buffer createBuffer(BufferDescriptor descriptor);

    void writeBuffer(Buffer buffer, ByteBuffer data);

    Texture createTexture(TextureDescriptor descriptor);

    void writeTexture(Texture texture, ByteBuffer data);

    ShaderModule createShaderModule(ShaderModuleDescriptor descriptor);

    RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor);
}
