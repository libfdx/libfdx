package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.core.FdxException;

/**
 * One compiled compute pass and all of its bounded variants.
 */
public final class ShaderGraphCompiledComputePass {
    private final ShaderGraphComputeTechniquePass pass;
    private final ShaderGraphCompiledComputeVariant[] variants;

    ShaderGraphCompiledComputePass(
            ShaderGraphComputeTechniquePass pass,
            ShaderGraphCompiledComputeVariant[] variants) {
        if (pass == null || variants == null
                || variants.length != pass.variants().length) {
            throw new FdxException(
                    "Compiled compute pass is incomplete");
        }
        this.pass = pass;
        this.variants = variants.clone();
    }

    public ShaderGraphComputeTechniquePass pass() {
        return pass;
    }

    public ShaderGraphCompiledComputeVariant[] variants() {
        return variants.clone();
    }

    public ShaderGraphCompiledComputeVariant variant(String key) {
        String normalized = ShaderGraphVariant.normalizeKey(key, true);
        for (ShaderGraphCompiledComputeVariant variant : variants) {
            if (variant.variant().key().equals(normalized)) {
                return variant;
            }
        }
        return null;
    }
}
