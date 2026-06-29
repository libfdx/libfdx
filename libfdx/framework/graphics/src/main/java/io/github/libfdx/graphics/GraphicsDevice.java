package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

import java.nio.ByteBuffer;

/**
 * Defines the contract for graphics device implementations.
 *
 * @author xpenatan
 */
public interface GraphicsDevice extends ProviderHandle {
    /**
     * Creates a buffer.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    Buffer createBuffer(BufferDescriptor descriptor);

    /**
     * Runs the write buffer step.
     *
     * @param buffer the buffer
     * @param data the data
     */
    void writeBuffer(Buffer buffer, ByteBuffer data);

    /**
     * Creates a texture.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    Texture createTexture(TextureDescriptor descriptor);

    /**
     * Runs the write texture step.
     *
     * @param texture the texture
     * @param data the data
     */
    void writeTexture(Texture texture, ByteBuffer data);

    /**
     * Creates a shader module.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    ShaderModule createShaderModule(ShaderModuleDescriptor descriptor);

    /**
     * Creates a render pipeline.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor);
}
