package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;

/**
 * One-pass picking view of the standard model technique.
 */
public final class StandardPickingTechnique {
    private final ShaderGraphRenderTechnique technique;

    private StandardPickingTechnique(
            ShaderGraphRenderTechnique technique) {
        this.technique = technique;
    }

    public static StandardPickingTechnique create(
            GraphicsContext graphics) {
        return new StandardPickingTechnique(
                StandardPbrTechnique.create(graphics)
                        .passTechnique(ShaderPassId.PICKING));
    }

    public ShaderGraphRenderTechnique technique() {
        return technique;
    }
}
