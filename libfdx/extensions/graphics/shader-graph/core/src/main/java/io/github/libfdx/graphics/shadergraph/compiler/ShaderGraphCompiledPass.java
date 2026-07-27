package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.core.FdxException;

/**
 * One compiled render pass and all of its bounded variants.
 */
public final class ShaderGraphCompiledPass {
    private final ShaderGraphTechniquePass pass;
    private final ShaderGraphCompiledVariant[] variants;

    ShaderGraphCompiledPass(ShaderGraphTechniquePass pass,
            ShaderGraphCompiledVariant[] variants) {
        if (pass == null || variants == null
                || variants.length != pass.variants().length) {
            throw new FdxException(
                    "Compiled shader graph pass is incomplete");
        }
        this.pass = pass;
        this.variants = variants.clone();
    }

    public ShaderGraphTechniquePass pass() {
        return pass;
    }

    public ShaderGraphCompiledVariant[] variants() {
        return variants.clone();
    }

    public ShaderGraphCompiledVariant variant(String key) {
        String requested = ShaderGraphVariant.normalizeKey(key, true);
        for (ShaderGraphCompiledVariant variant : variants) {
            if (variant.variant().key().equals(requested)) {
                return variant;
            }
        }
        return null;
    }
}
