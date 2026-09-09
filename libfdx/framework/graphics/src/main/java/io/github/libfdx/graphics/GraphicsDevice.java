package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.graphics.internal.TextureUploads;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Defines the contract for graphics device implementations.
 *
 * @author xpenatan
 */
public interface GraphicsDevice extends ProviderHandle {
    /**
     * Native resource-domain identity, compared by reference. Devices may share this token only
     * when their persistent resources are interchangeable. Provider identity alone is insufficient.
     * A device with loss/recreation must change its token when the old resources become invalid;
     * retained native preparation jobs remain the provider's responsibility until they drain.
     */
    default Object resourceDomain() { return this; }

    /** Actual preparation support. An unavailable provider never falls back to blocking draws. */
    default ShaderPreparationCapabilities shaderPreparationCapabilities() {
        return ShaderPreparationCapabilities.UNAVAILABLE;
    }

    /**
     * Starts complete preparation of an immutable source/pipeline packet. The provider owns
     * native workers, retained inputs, and deferred resource retirement. Runtime-capable
     * implementations must not compile or wait on this caller; publication happens through
     * the returned operation on the application thread. No synchronous fallback is supplied.
     */
    default ShaderPreparationOperation prepareRenderPipeline(
            ShaderPipelineRequest request) {
        throw new FdxException("Asynchronous render pipeline preparation is unavailable");
    }

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
     * method. Browser providers may suspend until mapping completes; perform
     * this read before recording uses of the current frame's surface attachment,
     * which cannot remain valid across that suspension.
     *
     * @param buffer source buffer
     * @param offset first byte
     * @param size byte count
     * @return direct buffer positioned at zero
     */
    default ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
        throw new FdxException(
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
     * Uploads the complete base of a one-level texture. Use {@link #writeTextureMipLevels} for
     * textures with multiple levels. Bytes begin at the buffer's current position, which is
     * preserved. The buffer is borrowed only for this call; no mip levels are generated.
     *
     * @param texture the texture
     * @param data the data
     */
    void writeTexture(Texture texture, ByteBuffer data);

    /** Uploads every allocated color mip in level order. Each buffer contains tightly packed texels
     * starting at its current position; positions/limits remain unchanged. Buffers are borrowed only
     * for this call. Every size is validated before writing. No mip generation occurs. Whole-chain
     * replacement preserves previously recorded draws on providers with delayed submission.
     * Use writeTexture(texture, data) for one-level textures. Native failures may leave partial data. */
    default void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
        TextureUploads.validate(texture, levels);
        if (levels.length != 1) throw new FdxException("Mip uploads are not supported by this provider");
        writeTexture(texture, levels[0]);
    }

    /**
     * Creates a persistent independently bindable sampler.
     *
     * @param descriptor sampler descriptor
     * @return the created sampler
     */
    default Sampler createSampler(SamplerDescriptor descriptor) {
        throw new FdxException(
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
     * Creates an application-owned batch of pipelines in descriptor order. This call is
     * synchronous: providers may compile independent stages concurrently, but all preparation
     * performed by this call finishes before it returns. Normal provider rules about work
     * deferred until first use still apply. Call on the device's owning thread, outside render
     * passes. Descriptors, their nested state, and borrowed shader modules must remain valid
     * and unchanged until the call returns.
     *
     * <p>On failure, pipelines created by this call are disposed and the error is propagated;
     * borrowed shader modules remain caller-owned. An empty batch returns an empty array.
     * The default implementation creates pipelines sequentially.</p>
     *
     * @param descriptors non-null descriptors for this device
     * @return new array of owned pipelines, one per descriptor
     */
    default RenderPipeline[] createRenderPipelines(RenderPipelineDescriptor... descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        for (RenderPipelineDescriptor descriptor : descriptors) {
            Objects.requireNonNull(descriptor, "descriptor");
        }
        RenderPipeline[] pipelines = new RenderPipeline[descriptors.length];
        try {
            for (int i = 0; i < descriptors.length; i++) pipelines[i] = createRenderPipeline(descriptors[i]);
            return pipelines;
        } catch (RuntimeException | Error failure) {
            for (int i = pipelines.length - 1; i >= 0; i--) if (pipelines[i] != null) {
                try { pipelines[i].dispose(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    /**
     * Creates a persistent compute pipeline.
     *
     * @param descriptor compute descriptor
     * @return the created pipeline
     */
    default ComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        throw new FdxException(
                "Compute pipelines are not supported by this graphics device");
    }
}
