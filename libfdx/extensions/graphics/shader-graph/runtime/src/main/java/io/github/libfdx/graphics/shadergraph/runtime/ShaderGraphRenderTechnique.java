package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * Complete immutable render-technique artifact assembled from graph-generated
 * or handwritten WGSL programs.
 */
public final class ShaderGraphRenderTechnique {
    private final String id;
    private final ShaderGraphRenderTechniquePass[] passes;

    private ShaderGraphRenderTechnique(String id,
            ShaderGraphRenderTechniquePass[] passes) {
        this.id = ShaderStableId.normalize(id,
                "Shader render technique");
        this.passes = passes != null ? passes.clone()
                : new ShaderGraphRenderTechniquePass[0];
        if (this.passes.length == 0) {
            throw new FdxException(
                    "Shader render technique requires a pass");
        }
        for (ShaderGraphRenderTechniquePass pass : this.passes) {
            if (pass == null) {
                throw new FdxException(
                        "Shader render technique passes must be non-null and unique");
            }
        }
        Arrays.sort(this.passes);
        for (int i = 0; i < this.passes.length; i++) {
            if (i > 0
                    && this.passes[i - 1].passId()
                            .equals(this.passes[i].passId())) {
                throw new FdxException(
                        "Shader render technique passes must be non-null and unique");
            }
        }
    }

    public static ShaderGraphRenderTechnique of(String id,
            ShaderGraphRenderTechniquePass... passes) {
        return new ShaderGraphRenderTechnique(id, passes);
    }

    public String id() {
        return id;
    }

    public ShaderGraphRenderTechniquePass[] passes() {
        return passes.clone();
    }

    public ShaderGraphRenderTechniquePass pass(
            ShaderPassId passId) {
        for (ShaderGraphRenderTechniquePass pass : passes) {
            if (pass.passId().equals(passId)) {
                return pass;
            }
        }
        return null;
    }
}
