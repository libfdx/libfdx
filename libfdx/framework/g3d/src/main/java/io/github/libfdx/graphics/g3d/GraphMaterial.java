package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialBinder;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialInstance;

/** Generic attribute-backed material with shader-graph values and resources. */
public final class GraphMaterial extends Material
        implements ShaderMaterialBinding {
    private final ShaderGraphMaterialInstance graphMaterial;
    private final ShaderGraphMaterialBinder binder;

    GraphMaterial(String id, ShaderGraphMaterialInstance graphMaterial) {
        super(id);
        if (graphMaterial == null) {
            throw new FdxException(
                    "Graph material instance cannot be null");
        }
        this.graphMaterial = graphMaterial;
        binder = new ShaderGraphMaterialBinder(
                graphMaterial.definition(), 1, 0);
        shaderBinding(this);
    }

    /** @return mutable graph-owned values and borrowed resources */
    public ShaderGraphMaterialInstance graphMaterial() {
        return graphMaterial;
    }

    @Override
    public long identity() {
        return graphMaterial.identity();
    }

    @Override
    public long revision() {
        return graphMaterial.revision();
    }

    @Override
    public void bind(RenderPass pass, ShaderResourceLayout layout) {
        binder.bindResources(pass, graphMaterial);
    }

    void writeParameters(ShaderResourceLayout layout,
            io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock block) {
        binder.writeParameters(layout, graphMaterial, block);
    }
}
