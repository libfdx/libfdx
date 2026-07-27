package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * Immutable collection of explicitly scheduled compute passes.
 */
public final class ShaderGraphComputeTechnique {
    private final String id;
    private final ShaderGraphComputeTechniquePass[] passes;
    private final int maxVariants;
    private final int variantCount;
    private final String semanticHash;

    private ShaderGraphComputeTechnique(Builder builder) {
        id = ShaderStableId.normalize(builder.id,
                "Shader compute technique");
        passes = builder.passes != null ? builder.passes.clone()
                : new ShaderGraphComputeTechniquePass[0];
        Arrays.sort(passes);
        if (passes.length == 0) {
            throw new FdxException(
                    "Compute technique requires at least one pass");
        }
        int count = 0;
        for (int i = 0; i < passes.length; i++) {
            if (passes[i] == null || i > 0
                    && passes[i - 1].passId()
                            .equals(passes[i].passId())) {
                throw new FdxException(
                        "Compute technique passes must be unique");
            }
            count += passes[i].variants().length;
        }
        if (builder.maxVariants <= 0
                || builder.maxVariants
                        > ShaderGraphTechnique.HARD_MAX_VARIANTS
                || count > builder.maxVariants) {
            throw new FdxException(
                    "Compute technique variant count exceeds its bound");
        }
        maxVariants = builder.maxVariants;
        variantCount = count;
        StringBuilder key = new StringBuilder(
                "fdx-compute-technique-v1\n")
                .append(id).append('\n').append(maxVariants).append('\n');
        for (ShaderGraphComputeTechniquePass pass : passes) {
            key.append(pass.semanticHash()).append('\n');
        }
        semanticHash = PortableSha256.hashUtf8(key.toString());
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public ShaderGraphComputeTechniquePass[] passes() {
        return passes.clone();
    }

    public ShaderGraphComputeTechniquePass pass(ShaderPassId id) {
        for (ShaderGraphComputeTechniquePass pass : passes) {
            if (pass.passId().equals(id)) {
                return pass;
            }
        }
        return null;
    }

    public int maxVariants() {
        return maxVariants;
    }

    public int variantCount() {
        return variantCount;
    }

    public String semanticHash() {
        return semanticHash;
    }

    /**
     * Mutable compute-technique construction scope.
     */
    public static final class Builder {
        private final String id;
        private ShaderGraphComputeTechniquePass[] passes =
                new ShaderGraphComputeTechniquePass[0];
        private int maxVariants =
                ShaderGraphTechnique.DEFAULT_MAX_VARIANTS;

        private Builder(String id) {
            this.id = id;
        }

        public Builder passes(
                ShaderGraphComputeTechniquePass... values) {
            passes = values != null ? values
                    : new ShaderGraphComputeTechniquePass[0];
            return this;
        }

        public Builder maxVariants(int value) {
            maxVariants = value;
            return this;
        }

        public ShaderGraphComputeTechnique build() {
            return new ShaderGraphComputeTechnique(this);
        }
    }
}
