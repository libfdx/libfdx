package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;

/**
 * One named render pass with complete state and bounded static variants.
 */
public final class ShaderGraphTechniquePass
        implements Comparable<ShaderGraphTechniquePass> {
    private final ShaderPassId passId;
    private final ShaderGraphPipelineState pipelineState;
    private final ShaderGraphVariant[] variants;
    private final String defaultVariantKey;
    private final String semanticHash;

    private ShaderGraphTechniquePass(Builder builder) {
        if (builder.passId == null || builder.pipelineState == null
                || builder.variants == null
                || builder.variants.length == 0) {
            throw new FdxException(
                    "Shader graph technique pass is incomplete");
        }
        passId = builder.passId;
        pipelineState = builder.pipelineState;
        variants = builder.variants.clone();
        Arrays.sort(variants);
        for (int i = 0; i < variants.length; i++) {
            if (variants[i] == null) {
                throw new FdxException(
                        "Shader graph technique variant cannot be null");
            }
            if (i > 0 && variants[i - 1].key()
                    .equals(variants[i].key())) {
                throw new FdxException("Duplicate shader variant "
                        + variants[i].key() + " in pass " + passId);
            }
        }
        defaultVariantKey = builder.defaultVariantKey != null
                ? ShaderGraphVariant.normalizeKey(
                        builder.defaultVariantKey, true)
                : ShaderGraphVariant.DEFAULT_KEY;
        if (variant(defaultVariantKey) == null) {
            throw new FdxException("Shader pass " + passId
                    + " has no declared default variant "
                    + defaultVariantKey);
        }
        validateFallbacks();
        semanticHash = PortableSha256.hashUtf8(semanticKey());
    }

    public static Builder builder(ShaderPassId passId,
            ShaderGraphPipelineState pipelineState) {
        return new Builder(passId, pipelineState);
    }

    public ShaderPassId passId() {
        return passId;
    }

    public ShaderGraphPipelineState pipelineState() {
        return pipelineState;
    }

    public ShaderGraphVariant[] variants() {
        return variants.clone();
    }

    public String defaultVariantKey() {
        return defaultVariantKey;
    }

    public ShaderGraphVariant variant(String key) {
        String requested = ShaderGraphVariant.normalizeKey(key, true);
        for (ShaderGraphVariant variant : variants) {
            if (variant.key().equals(requested)) {
                return variant;
            }
        }
        return null;
    }

    public String semanticHash() {
        return semanticHash;
    }

    @Override
    public int compareTo(ShaderGraphTechniquePass other) {
        return passId.compareTo(other.passId);
    }

    private void validateFallbacks() {
        for (ShaderGraphVariant start : variants) {
            ShaderGraphVariant current = start;
            for (int depth = 0; current.fallbackKey() != null; depth++) {
                if (depth >= variants.length) {
                    throw new FdxException(
                            "Shader variant fallback cycle in pass " + passId);
                }
                ShaderGraphVariant fallback =
                        variant(current.fallbackKey());
                if (fallback == null) {
                    throw new FdxException("Shader variant "
                            + current.key() + " in pass " + passId
                            + " references missing fallback "
                            + current.fallbackKey());
                }
                if (fallback == start) {
                    throw new FdxException(
                            "Shader variant fallback cycle in pass " + passId);
                }
                current = fallback;
            }
        }
    }

    private String semanticKey() {
        StringBuilder value = new StringBuilder("fdx-technique-pass-v1\n")
                .append(passId.value()).append('\n')
                .append(pipelineState.semanticHash()).append('\n')
                .append(defaultVariantKey).append('\n')
                .append(variants.length).append('\n');
        for (ShaderGraphVariant variant : variants) {
            value.append(variant.key()).append('\n')
                    .append(variant.program().semanticHash()).append('\n')
                    .append(variant.fallbackKey() != null
                            ? variant.fallbackKey() : "-")
                    .append('\n');
            for (ShaderGraphStaticValue staticValue
                    : variant.staticValues()) {
                value.append(staticValue.parameterId()).append('=')
                        .append(staticValue.boolValue()).append('\n');
            }
            for (var profile : variant.profiles()) {
                value.append("profile:").append(profile.name()).append('\n');
            }
            for (var feature : variant.features()) {
                value.append("feature:").append(feature.name()).append('\n');
            }
        }
        return value.toString();
    }

    /**
     * Mutable pass construction scope.
     */
    public static final class Builder {
        private final ShaderPassId passId;
        private final ShaderGraphPipelineState pipelineState;
        private ShaderGraphVariant[] variants = new ShaderGraphVariant[0];
        private String defaultVariantKey;

        private Builder(ShaderPassId passId,
                ShaderGraphPipelineState pipelineState) {
            this.passId = passId;
            this.pipelineState = pipelineState;
        }

        public Builder variants(ShaderGraphVariant... values) {
            variants = values != null ? values
                    : new ShaderGraphVariant[0];
            return this;
        }

        public Builder defaultVariant(String key) {
            defaultVariantKey = key;
            return this;
        }

        public ShaderGraphTechniquePass build() {
            return new ShaderGraphTechniquePass(this);
        }
    }
}
