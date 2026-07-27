package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * One explicit, bounded static program variant and its capability fallback.
 */
public final class ShaderGraphVariant
        implements Comparable<ShaderGraphVariant> {
    public static final String DEFAULT_KEY = "";

    private final String key;
    private final ShaderGraphProgram sourceProgram;
    private final ShaderGraphProgram program;
    private final ShaderGraphStaticValue[] staticValues;
    private final ShaderProfile[] profiles;
    private final GraphicsFeature[] features;
    private final String fallbackKey;

    private ShaderGraphVariant(Builder builder) {
        key = normalizeKey(builder.key, true);
        if (builder.program == null) {
            throw new FdxException("Shader graph variant requires a program");
        }
        sourceProgram = builder.program;
        staticValues = sortedUnique(builder.staticValues,
                "static switch");
        profiles = sortedUnique(builder.profiles, "profile");
        features = sortedUnique(builder.features, "feature");
        fallbackKey = builder.fallbackKey != null
                ? normalizeKey(builder.fallbackKey, true) : null;
        if (fallbackKey != null && fallbackKey.equals(key)) {
            throw new FdxException(
                    "Shader graph variant cannot fall back to itself: " + key);
        }
        program = specialize(sourceProgram, staticValues);
    }

    public static Builder builder(String key, ShaderGraphProgram program) {
        return new Builder(key, program);
    }

    public String key() {
        return key;
    }

    /**
     * Returns the unspecialized authoring program.
     */
    public ShaderGraphProgram sourceProgram() {
        return sourceProgram;
    }

    /**
     * Returns the program with all declared static switch values applied.
     */
    public ShaderGraphProgram program() {
        return program;
    }

    public ShaderGraphStaticValue[] staticValues() {
        return staticValues.clone();
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

    public boolean supports(ShaderProfile profile,
            GraphicsCapabilities capabilities) {
        if (profile == null || capabilities == null
                || !capabilities.supports(profile)) {
            return false;
        }
        if (profiles.length > 0
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
    public int compareTo(ShaderGraphVariant other) {
        return key.compareTo(other.key);
    }

    /**
     * Normalizes a variant key using the same rules as variant builders and
     * compiled-pass lookup.
     *
     * @param value the key
     * @param allowDefault whether a null or empty key selects the default
     * @return the normalized key
     */
    public static String normalizeKey(String value, boolean allowDefault) {
        if (value == null || value.trim().isEmpty()) {
            if (allowDefault) {
                return DEFAULT_KEY;
            }
            throw new FdxException("Shader variant key cannot be empty");
        }
        return ShaderStableId.normalize(value, "Shader variant key");
    }

    private static ShaderGraphProgram specialize(ShaderGraphProgram source,
            ShaderGraphStaticValue[] values) {
        if (values.length == 0) {
            return source;
        }
        boolean[] applied = new boolean[values.length];
        ShaderGraph vertex = specialize(source.vertex(), values, applied);
        ShaderGraph fragment = specialize(source.fragment(), values, applied);
        for (int i = 0; i < values.length; i++) {
            if (!applied[i]) {
                throw new FdxException("Static switch "
                        + values[i].parameterId()
                        + " does not exist in program " + source.id());
            }
        }
        return ShaderGraphProgram.builder(source.id().value(),
                        vertex, fragment)
                .entryPoints(source.vertexEntryPoint(),
                        source.fragmentEntryPoint())
                .materialBinding(source.materialGroup(),
                        source.materialBinding())
                .build();
    }

    private static ShaderGraph specialize(ShaderGraph graph,
            ShaderGraphStaticValue[] values, boolean[] applied) {
        ShaderGraphParameter[] parameters = graph.parameters();
        boolean changed = false;
        for (int i = 0; i < parameters.length; i++) {
            ShaderGraphParameter parameter = parameters[i];
            int valueIndex = value(values, parameter.id());
            if (valueIndex < 0) {
                continue;
            }
            if (parameter.kind() != ShaderGraphParameterKind.STATIC_SWITCH) {
                throw new FdxException("Static variant value targets non-static "
                        + "parameter " + parameter.id() + " in " + graph.id());
            }
            parameters[i] = ShaderGraphParameter.semantic(
                    parameter.id().value(), parameter.type(),
                    parameter.kind(), values[valueIndex].literal(),
                    parameter.semantic());
            applied[valueIndex] = true;
            changed = true;
        }
        if (!changed) {
            return graph;
        }
        return ShaderGraph.builder(graph.id().value(), graph.kind())
                .formatVersion(graph.formatVersion())
                .parameters(parameters)
                .resources(graph.resources())
                .nodes(graph.nodes())
                .edges(graph.edges())
                .outputs(graph.outputs())
                .dependencies(graph.dependencies())
                .build();
    }

    private static int value(ShaderGraphStaticValue[] values,
            ShaderGraphId parameter) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].parameterId().equals(parameter)) {
                return i;
            }
        }
        return -1;
    }

    private static <T extends Comparable<? super T>> T[] sortedUnique(
            T[] values, String kind) {
        if (values == null) {
            throw new FdxException("Shader variant " + kind
                    + " values cannot be null");
        }
        T[] result = values.clone();
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                throw new FdxException("Shader variant " + kind
                        + " cannot be null");
            }
            if (i > 0 && result[i - 1].compareTo(result[i]) == 0) {
                throw new FdxException("Duplicate shader variant " + kind
                        + ": " + result[i]);
            }
        }
        return result;
    }

    /**
     * Mutable variant construction scope.
     */
    public static final class Builder {
        private final String key;
        private final ShaderGraphProgram program;
        private ShaderGraphStaticValue[] staticValues =
                new ShaderGraphStaticValue[0];
        private ShaderProfile[] profiles = new ShaderProfile[0];
        private GraphicsFeature[] features = new GraphicsFeature[0];
        private String fallbackKey;

        private Builder(String key, ShaderGraphProgram program) {
            this.key = key;
            this.program = program;
        }

        public Builder staticValues(ShaderGraphStaticValue... values) {
            staticValues = values != null ? values
                    : new ShaderGraphStaticValue[0];
            return this;
        }

        public Builder profiles(ShaderProfile... values) {
            profiles = values != null ? values : new ShaderProfile[0];
            return this;
        }

        public Builder features(GraphicsFeature... values) {
            features = values != null ? values : new GraphicsFeature[0];
            return this;
        }

        public Builder fallback(String key) {
            fallbackKey = key;
            return this;
        }

        public ShaderGraphVariant build() {
            return new ShaderGraphVariant(this);
        }
    }
}
