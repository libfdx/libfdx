package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;

/**
 * One-pass shadow view of the standard model technique.
 */
public final class StandardShadowTechnique {
    private final ShaderGraphRenderTechnique technique;

    private StandardShadowTechnique(
            ShaderGraphRenderTechnique technique) {
        this.technique = technique;
    }

    public static StandardShadowTechnique create(
            GraphicsContext graphics) {
        return new StandardShadowTechnique(
                StandardPbrTechnique.create(graphics)
                        .passTechnique(ShaderPassId.SHADOW));
    }

    public ShaderGraphRenderTechnique technique() {
        return technique;
    }
}
