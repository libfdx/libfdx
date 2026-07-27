package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;

/**
 * One explicitly scheduled named compute pass.
 */
public final class ShaderGraphComputeTechniquePass
        implements Comparable<ShaderGraphComputeTechniquePass> {
    private final ShaderPassId passId;
    private final ShaderGraphComputeVariant[] variants;
    private final String defaultVariantKey;
    private final String semanticHash;

    private ShaderGraphComputeTechniquePass(Builder builder) {
        if (builder.passId == null || builder.variants == null
                || builder.variants.length == 0) {
            throw new FdxException(
                    "Shader graph compute pass is incomplete");
        }
        passId = builder.passId;
        variants = builder.variants.clone();
        Arrays.sort(variants);
        for (int i = 0; i < variants.length; i++) {
            if (variants[i] == null || i > 0
                    && variants[i - 1].key()
                            .equals(variants[i].key())) {
                throw new FdxException(
                        "Compute pass variants must be unique");
            }
        }
        defaultVariantKey = ShaderGraphVariant.normalizeKey(
                builder.defaultVariantKey, true);
        if (variant(defaultVariantKey) == null) {
            throw new FdxException("Compute pass " + passId
                    + " has no default variant " + defaultVariantKey);
        }
        validateFallbacks();
        StringBuilder key = new StringBuilder(
                "fdx-compute-pass-v1\n")
                .append(passId.value()).append('\n')
                .append(defaultVariantKey).append('\n');
        for (ShaderGraphComputeVariant variant : variants) {
            key.append(variant.key()).append('\n')
                    .append(variant.program().semanticHash()).append('\n')
                    .append(variant.fallbackKey() != null
                            ? variant.fallbackKey() : "-")
                    .append('\n');
        }
        semanticHash = PortableSha256.hashUtf8(key.toString());
    }

    public static Builder builder(ShaderPassId passId) {
        return new Builder(passId);
    }

    public ShaderPassId passId() {
        return passId;
    }

    public ShaderGraphComputeVariant[] variants() {
        return variants.clone();
    }

    public String defaultVariantKey() {
        return defaultVariantKey;
    }

    public ShaderGraphComputeVariant variant(String key) {
        String normalized = ShaderGraphVariant.normalizeKey(key, true);
        for (ShaderGraphComputeVariant variant : variants) {
            if (variant.key().equals(normalized)) {
                return variant;
            }
        }
        return null;
    }

    public String semanticHash() {
        return semanticHash;
    }

    @Override
    public int compareTo(ShaderGraphComputeTechniquePass other) {
        return passId.compareTo(other.passId);
    }

    private void validateFallbacks() {
        for (ShaderGraphComputeVariant start : variants) {
            ShaderGraphComputeVariant current = start;
            for (int depth = 0; current.fallbackKey() != null;
                    depth++) {
                if (depth >= variants.length) {
                    throw new FdxException(
                            "Compute variant fallback cycle in pass "
                                    + passId);
                }
                ShaderGraphComputeVariant fallback =
                        variant(current.fallbackKey());
                if (fallback == null) {
                    throw new FdxException("Compute variant "
                            + current.key() + " references missing fallback "
                            + current.fallbackKey());
                }
                if (fallback == start) {
                    throw new FdxException(
                            "Compute variant fallback cycle in pass "
                                    + passId);
                }
                current = fallback;
            }
        }
    }

    /**
     * Mutable compute-pass construction scope.
     */
    public static final class Builder {
        private final ShaderPassId passId;
        private ShaderGraphComputeVariant[] variants =
                new ShaderGraphComputeVariant[0];
        private String defaultVariantKey = "";

        private Builder(ShaderPassId passId) {
            this.passId = passId;
        }

        public Builder variants(ShaderGraphComputeVariant... values) {
            variants = values != null ? values
                    : new ShaderGraphComputeVariant[0];
            return this;
        }

        public Builder defaultVariant(String value) {
            defaultVariantKey = value;
            return this;
        }

        public ShaderGraphComputeTechniquePass build() {
            return new ShaderGraphComputeTechniquePass(this);
        }
    }
}
