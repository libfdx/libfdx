package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * Immutable, UI-independent collection of named shader passes.
 *
 * <p>The technique describes programs, variants, requirements, and state. It
 * never schedules or submits the passes.</p>
 */
public final class ShaderGraphTechnique {
    public static final int DEFAULT_MAX_VARIANTS = 128;
    public static final int HARD_MAX_VARIANTS = 1024;

    private final String id;
    private final ShaderGraphTechniquePass[] passes;
    private final int maxVariants;
    private final int variantCount;
    private final String semanticHash;

    private ShaderGraphTechnique(Builder builder) {
        id = ShaderStableId.normalize(builder.id, "Shader technique");
        passes = builder.passes != null ? builder.passes.clone()
                : new ShaderGraphTechniquePass[0];
        Arrays.sort(passes);
        if (passes.length == 0) {
            throw new FdxException(
                    "Shader graph technique requires at least one pass");
        }
        int count = 0;
        for (int i = 0; i < passes.length; i++) {
            if (passes[i] == null) {
                throw new FdxException(
                        "Shader graph technique pass cannot be null");
            }
            if (i > 0 && passes[i - 1].passId()
                    .equals(passes[i].passId())) {
                throw new FdxException("Duplicate shader technique pass "
                        + passes[i].passId());
            }
            count += passes[i].variants().length;
        }
        if (builder.maxVariants <= 0
                || builder.maxVariants > HARD_MAX_VARIANTS) {
            throw new FdxException("Shader technique variant limit must be "
                    + "within 1.." + HARD_MAX_VARIANTS);
        }
        maxVariants = builder.maxVariants;
        variantCount = count;
        if (variantCount > maxVariants) {
            throw new FdxException("Shader technique " + id + " expands to "
                    + variantCount + " variants, limit is " + maxVariants);
        }
        StringBuilder key = new StringBuilder("fdx-technique-v1\n")
                .append(id).append('\n').append(maxVariants).append('\n');
        for (ShaderGraphTechniquePass pass : passes) {
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

    public ShaderGraphTechniquePass[] passes() {
        return passes.clone();
    }

    public ShaderGraphTechniquePass pass(ShaderPassId passId) {
        if (passId == null) {
            return null;
        }
        for (ShaderGraphTechniquePass pass : passes) {
            if (pass.passId().equals(passId)) {
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
     * Mutable technique construction scope.
     */
    public static final class Builder {
        private final String id;
        private ShaderGraphTechniquePass[] passes =
                new ShaderGraphTechniquePass[0];
        private int maxVariants = DEFAULT_MAX_VARIANTS;

        private Builder(String id) {
            this.id = id;
        }

        public Builder passes(ShaderGraphTechniquePass... values) {
            passes = values != null ? values
                    : new ShaderGraphTechniquePass[0];
            return this;
        }

        public Builder maxVariants(int value) {
            maxVariants = value;
            return this;
        }

        public ShaderGraphTechnique build() {
            return new ShaderGraphTechnique(this);
        }
    }
}
