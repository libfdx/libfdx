package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for command encoder implementations.
 *
 * @author xpenatan
 */
public interface CommandEncoder extends ProviderHandle {
    /**
     * Begins render pass.
     *
     * @param descriptor the descriptor
     * @return the begin render pass
     */
    RenderPass beginRenderPass(RenderPassDescriptor descriptor);

    /**
     * Begins a compute command scope.
     *
     * @param descriptor descriptor
     * @return borrowed compute pass
     */
    default ComputePass beginComputePass(ComputePassDescriptor descriptor) {
        throw new io.github.libfdx.core.FdxException(
                "Compute passes are not supported by this command encoder");
    }

    /**
     * Copies a buffer range. Recorded-command providers retain the referenced
     * allocations until submission or abandonment.
     *
     * @param source source buffer
     * @param sourceOffset source byte offset
     * @param destination destination buffer
     * @param destinationOffset destination byte offset
     * @param size byte count
     */
    default void copyBufferToBuffer(Buffer source, int sourceOffset,
            Buffer destination, int destinationOffset, int size) {
        throw new io.github.libfdx.core.FdxException(
                "Buffer copies are not supported by this command encoder");
    }
}
