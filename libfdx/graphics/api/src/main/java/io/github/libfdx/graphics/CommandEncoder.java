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
}
