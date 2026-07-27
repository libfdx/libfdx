package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;

/**
 * One-pass depth view of the standard model technique.
 */
public final class StandardDepthTechnique {
    private final ShaderGraphRenderTechnique technique;

    private StandardDepthTechnique(
            ShaderGraphRenderTechnique technique) {
        this.technique = technique;
    }

    public static StandardDepthTechnique create(
            GraphicsContext graphics) {
        return new StandardDepthTechnique(
                StandardPbrTechnique.create(graphics)
                        .passTechnique(ShaderPassId.DEPTH));
    }

    public ShaderGraphRenderTechnique technique() {
        return technique;
    }
}
