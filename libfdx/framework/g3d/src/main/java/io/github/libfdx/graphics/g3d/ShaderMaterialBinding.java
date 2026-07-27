package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding;

/**
 * Optional provider-neutral material resources bound by a G3D shader adapter.
 *
 * <p>The material owns its values and borrows textures/samplers. Binding does
 * not transfer ownership of the render pass, layout, or resources.</p>
 */
public interface ShaderMaterialBinding extends ShaderResourceBinding {
}
