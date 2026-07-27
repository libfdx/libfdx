package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;

/**
 * Provider-neutral binding operation for resources owned outside a renderer.
 *
 * <p>The binding borrows every referenced parameter block, buffer, texture,
 * and sampler. Implementations expose stable identity and revision values so
 * delayed renderers can reject mutation after accepting draw data.</p>
 */
public interface ShaderResourceBinding {
    /**
     * Stable identity of this binding instance.
     *
     * @return non-negative identity
     */
    long identity();

    /**
     * Monotonic revision of the borrowed values.
     *
     * @return non-negative revision
     */
    long revision();

    /**
     * Binds values required by the resolved resource layout.
     *
     * @param pass active render pass
     * @param layout resolved shader resource layout
     */
    void bind(RenderPass pass, ShaderResourceLayout layout);
}
