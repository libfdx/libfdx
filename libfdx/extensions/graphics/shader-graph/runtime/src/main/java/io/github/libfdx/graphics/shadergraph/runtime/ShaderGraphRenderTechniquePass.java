package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;

import java.util.Arrays;

/**
 * One named pass in a packaged render technique.
 */
public final class ShaderGraphRenderTechniquePass
        implements Comparable<ShaderGraphRenderTechniquePass> {
    private final ShaderPassId passId;
    private final ShaderGraphPipelineState pipelineState;
    private final ShaderGraphRenderVariant[] variants;
    private final String defaultVariantKey;

    private ShaderGraphRenderTechniquePass(Builder builder) {
        if (builder.passId == null || builder.variants == null
                || builder.variants.length == 0) {
            throw new FdxException(
                    "Shader render technique pass is incomplete");
        }
        passId = builder.passId;
        pipelineState = builder.pipelineState;
        variants = builder.variants.clone();
        for (ShaderGraphRenderVariant variant : variants) {
            if (variant == null) {
                throw new FdxException(
                        "Shader render technique variants must be non-null and unique");
            }
        }
        Arrays.sort(variants);
        for (int i = 0; i < variants.length; i++) {
            if (i > 0
                    && variants[i - 1].key()
                            .equals(variants[i].key())) {
                throw new FdxException(
                        "Shader render technique variants must be non-null and unique");
            }
        }
        defaultVariantKey =
                ShaderGraphRenderVariant.normalizeKey(
                        builder.defaultVariantKey, true);
        if (variant(defaultVariantKey) == null) {
            throw new FdxException(
                    "Shader render technique default variant is missing");
        }
        validateFallbacks();
    }

    public static Builder builder(ShaderPassId passId) {
        return new Builder(passId);
    }

    public ShaderPassId passId() {
        return passId;
    }

    /**
     * Returns complete graph-owned pipeline state when this pass came from a
     * compiled package. Handwritten render programs may leave it unspecified
     * and use request-compatible state.
     */
    public ShaderGraphPipelineState pipelineState() {
        return pipelineState;
    }

    public ShaderGraphRenderVariant[] variants() {
        return variants.clone();
    }

    public String defaultVariantKey() {
        return defaultVariantKey;
    }

    public ShaderGraphRenderVariant variant(String key) {
        String normalized =
                ShaderGraphRenderVariant.normalizeKey(key, true);
        for (ShaderGraphRenderVariant variant : variants) {
            if (variant.key().equals(normalized)) {
                return variant;
            }
        }
        return null;
    }

    @Override
    public int compareTo(
            ShaderGraphRenderTechniquePass other) {
        return passId.compareTo(other.passId);
    }

    private void validateFallbacks() {
        for (ShaderGraphRenderVariant start : variants) {
            ShaderGraphRenderVariant current = start;
            for (int depth = 0;
                    current.fallbackKey() != null; depth++) {
                if (depth >= variants.length) {
                    throw new FdxException(
                            "Shader render variant fallback cycle");
                }
                current = variant(current.fallbackKey());
                if (current == null) {
                    throw new FdxException(
                            "Shader render variant fallback is missing");
                }
                if (current == start) {
                    throw new FdxException(
                            "Shader render variant fallback cycle");
                }
            }
        }
    }

    /**
     * Mutable pass construction scope.
     */
    public static final class Builder {
        private final ShaderPassId passId;
        private ShaderGraphPipelineState pipelineState;
        private ShaderGraphRenderVariant[] variants =
                new ShaderGraphRenderVariant[0];
        private String defaultVariantKey = "";

        private Builder(ShaderPassId passId) {
            this.passId = passId;
        }

        public Builder variants(
                ShaderGraphRenderVariant... values) {
            variants = values != null ? values
                    : new ShaderGraphRenderVariant[0];
            return this;
        }

        public Builder pipelineState(
                ShaderGraphPipelineState value) {
            pipelineState = value;
            return this;
        }

        public Builder defaultVariant(String value) {
            defaultVariantKey = value;
            return this;
        }

        public ShaderGraphRenderTechniquePass build() {
            return new ShaderGraphRenderTechniquePass(this);
        }
    }
}
