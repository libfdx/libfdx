package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * One immutable static variant of a packaged render program.
 */
public final class ShaderGraphRenderVariant
        implements Comparable<ShaderGraphRenderVariant> {
    private final String key;
    private final ShaderGraphRenderProgram program;
    private final ShaderProfile[] profiles;
    private final GraphicsFeature[] features;
    private final String fallbackKey;
    private final ShaderProfile compiledProfile;

    private ShaderGraphRenderVariant(Builder builder) {
        key = normalizeKey(builder.key, true);
        if (builder.program == null) {
            throw new FdxException(
                    "Shader render variant requires a program");
        }
        program = builder.program;
        profiles = sorted(builder.profiles, "profile");
        features = sorted(builder.features, "feature");
        fallbackKey = builder.fallbackKey != null
                ? normalizeKey(builder.fallbackKey, true) : null;
        compiledProfile = builder.compiledProfile;
        if (key.equals(fallbackKey)) {
            throw new FdxException(
                    "Shader render variant cannot fall back to itself");
        }
    }

    public static Builder builder(String key,
            ShaderGraphRenderProgram program) {
        return new Builder(key, program);
    }

    public String key() {
        return key;
    }

    public ShaderGraphRenderProgram program() {
        return program;
    }

    public ShaderProfile[] profiles() {
        return profiles.clone();
    }

    public GraphicsFeature[] features() {
        return features.clone();
    }

    public String fallbackKey() {
        return fallbackKey;
    }

    /**
     * Profile used to compile a loaded document artifact, or {@code null} for
     * a directly supplied runtime program.
     */
    public ShaderProfile compiledProfile() {
        return compiledProfile;
    }

    public boolean supports(ShaderProfile profile,
            GraphicsCapabilities capabilities) {
        if (profile == null || capabilities == null
                || !capabilities.supports(profile)
                || compiledProfile != null
                        && compiledProfile != profile
                || profiles.length > 0
                        && Arrays.binarySearch(profiles, profile) < 0) {
            return false;
        }
        for (GraphicsFeature feature : features) {
            if (!capabilities.supports(feature)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(ShaderGraphRenderVariant other) {
        return key.compareTo(other.key);
    }

    static String normalizeKey(String value,
            boolean allowDefault) {
        if (value == null || value.trim().isEmpty()) {
            if (allowDefault) {
                return "";
            }
            throw new FdxException(
                    "Shader render variant key cannot be empty");
        }
        return ShaderStableId.normalize(value,
                "Shader render variant");
    }

    private static <T extends Comparable<? super T>> T[] sorted(
            T[] values, String kind) {
        if (values == null) {
            throw new FdxException(
                    "Shader render variant " + kind
                            + " values cannot be null");
        }
        T[] result = values.clone();
        for (T value : result) {
            if (value == null) {
                throw new FdxException(
                        "Shader render variant " + kind
                                + " values must be non-null and unique");
            }
        }
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            if (i > 0
                    && result[i - 1].compareTo(result[i]) == 0) {
                throw new FdxException(
                        "Shader render variant " + kind
                                + " values must be non-null and unique");
            }
        }
        return result;
    }

    /**
     * Mutable variant construction scope.
     */
    public static final class Builder {
        private final String key;
        private final ShaderGraphRenderProgram program;
        private ShaderProfile[] profiles = new ShaderProfile[0];
        private GraphicsFeature[] features = new GraphicsFeature[0];
        private String fallbackKey;
        private ShaderProfile compiledProfile;

        private Builder(String key,
                ShaderGraphRenderProgram program) {
            this.key = key;
            this.program = program;
        }

        public Builder profiles(ShaderProfile... values) {
            profiles = values != null ? values
                    : new ShaderProfile[0];
            return this;
        }

        public Builder features(GraphicsFeature... values) {
            features = values != null ? values
                    : new GraphicsFeature[0];
            return this;
        }

        public Builder fallback(String value) {
            fallbackKey = value;
            return this;
        }

        /**
         * Locks this variant to the profile used to compile its artifact.
         */
        public Builder compiledProfile(ShaderProfile value) {
            if (value == null) {
                throw new FdxException(
                        "Compiled shader profile cannot be null");
            }
            compiledProfile = value;
            return this;
        }

        public ShaderGraphRenderVariant build() {
            return new ShaderGraphRenderVariant(this);
        }
    }
}
