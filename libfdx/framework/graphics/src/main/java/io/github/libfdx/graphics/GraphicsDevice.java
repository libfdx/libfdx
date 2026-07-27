package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import io.github.libfdx.core.ProviderHandle;

import java.nio.ByteBuffer;

/**
 * Defines the contract for graphics device implementations.
 *
 * @author xpenatan
 */
public interface GraphicsDevice extends ProviderHandle {
    /**
     * Returns immutable capabilities and limits for this device.
     *
     * @return the device capabilities
     */
    default GraphicsCapabilities capabilities() {
        return GraphicsCapabilities.conservativeRender();
    }

    /**
     * Returns immutable shader target formats accepted by this provider.
     *
     * @return the accepted target support
     */
    default ShaderTargetSupport shaderTargetSupport() {
        return ShaderTargetSupport.forProvider(providerId());
    }

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
     * Reads a completed GPU buffer range into new direct storage. Callers must
     * submit any command encoder that writes the range before invoking this
     * method.
     *
     * @param buffer source buffer
     * @param offset first byte
     * @param size byte count
     * @return direct buffer positioned at zero
     */
    default ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
        throw new io.github.libfdx.core.FdxException(
                "Buffer readback is not supported by this graphics device");
    }

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
     * Creates a persistent independently bindable sampler.
     *
     * @param descriptor sampler descriptor
     * @return the created sampler
     */
    default Sampler createSampler(SamplerDescriptor descriptor) {
        throw new io.github.libfdx.core.FdxException(
                "Separate samplers are not supported by this graphics device");
    }

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

    /**
     * Creates a persistent compute pipeline.
     *
     * @param descriptor compute descriptor
     * @return the created pipeline
     */
    default ComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        throw new io.github.libfdx.core.FdxException(
                "Compute pipelines are not supported by this graphics device");
    }
}
