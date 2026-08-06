package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;

/**
 * Standard graph-composed unlit model technique.
 *
 * <p>It retains the ModelBatch PBR geometry/material ABI but replaces final
 * light accumulation with base color plus emissive. This makes it usable by
 * the same common {@code ShaderProvider} bridge and static/skinned variants.</p>
 */
public final class StandardUnlitTechnique {
    private final StandardPbrTechnique delegate;

    private StandardUnlitTechnique(StandardPbrTechnique delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates the standard unlit technique.
     *
     * @param graphics graphics context
     * @return unlit technique
     */
    public static StandardUnlitTechnique create(
            GraphicsContext graphics) {
        return create(graphics,
                StandardPbrSurfaceGraph.create());
    }

    /**
     * Creates an unlit technique with custom surface evaluation.
     *
     * @param graphics graphics context
     * @param surfaceGraph surface graph
     * @return unlit technique
     */
    public static StandardUnlitTechnique create(
            GraphicsContext graphics, ShaderGraph surfaceGraph) {
        return new StandardUnlitTechnique(
                StandardPbrTechnique.builder(graphics)
                        .surfaceGraph(surfaceGraph)
                        .lightingGraph(
                                StandardPbrLightingGraph.unlit())
                        .build());
    }

    public ShaderGraphRenderTechnique technique() {
        return delegate.technique();
    }

    public ShaderGraphRenderTechnique passTechnique(
            ShaderPassId passId) {
        return delegate.passTechnique(passId);
    }

    public GraphMaterial material(String id) {
        GraphMaterial material = delegate.material(id);
        material.shadingModel(ShadingModel.UNLIT);
        return material;
    }

    public ShaderGraphMaterialDefinition materialDefinition() {
        return delegate.materialDefinition();
    }
}
