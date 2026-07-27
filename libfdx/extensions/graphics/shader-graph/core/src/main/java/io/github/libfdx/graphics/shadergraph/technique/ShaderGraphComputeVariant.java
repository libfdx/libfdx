package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderProfile;

import java.util.Arrays;

/**
 * One explicit compute-program variant and capability fallback.
 */
public final class ShaderGraphComputeVariant
        implements Comparable<ShaderGraphComputeVariant> {
    private final String key;
    private final ShaderGraphComputeProgram sourceProgram;
    private final ShaderGraphComputeProgram program;
    private final ShaderGraphStaticValue[] staticValues;
    private final ShaderProfile[] profiles;
    private final GraphicsFeature[] features;
    private final String fallbackKey;

    private ShaderGraphComputeVariant(Builder builder) {
        key = ShaderGraphVariant.normalizeKey(builder.key, true);
        if (builder.program == null) {
            throw new FdxException(
                    "Shader graph compute variant requires a program");
        }
        sourceProgram = builder.program;
        staticValues = sortedUnique(builder.staticValues,
                "static switch");
        profiles = sortedUnique(builder.profiles, "profile");
        features = sortedUnique(builder.features, "feature");
        fallbackKey = builder.fallbackKey != null
                ? ShaderGraphVariant.normalizeKey(
                        builder.fallbackKey, true) : null;
        if (fallbackKey != null && fallbackKey.equals(key)) {
            throw new FdxException(
                    "Compute variant cannot fall back to itself: " + key);
        }
        program = specialize(sourceProgram, staticValues);
    }

    public static Builder builder(String key,
            ShaderGraphComputeProgram program) {
        return new Builder(key, program);
    }

    public String key() {
        return key;
    }

    public ShaderGraphComputeProgram sourceProgram() {
        return sourceProgram;
    }

    public ShaderGraphComputeProgram program() {
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
                || !capabilities.supports(profile)
                || !capabilities.supports(GraphicsFeature.COMPUTE)) {
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
    public int compareTo(ShaderGraphComputeVariant other) {
        return key.compareTo(other.key);
    }

    private static ShaderGraphComputeProgram specialize(
            ShaderGraphComputeProgram source,
            ShaderGraphStaticValue[] values) {
        if (values.length == 0) {
            return source;
        }
        ShaderGraph graph = source.graph();
        ShaderGraphParameter[] parameters = graph.parameters();
        boolean[] applied = new boolean[values.length];
        for (int i = 0; i < parameters.length; i++) {
            int value = value(values, parameters[i].id());
            if (value < 0) {
                continue;
            }
            if (parameters[i].kind()
                    != ShaderGraphParameterKind.STATIC_SWITCH) {
                throw new FdxException(
                        "Compute variant static value targets non-static parameter "
                                + parameters[i].id());
            }
            parameters[i] = ShaderGraphParameter.semantic(
                    parameters[i].id().value(), parameters[i].type(),
                    parameters[i].kind(), values[value].literal(),
                    parameters[i].semantic());
            applied[value] = true;
        }
        for (int i = 0; i < applied.length; i++) {
            if (!applied[i]) {
                throw new FdxException("Static switch "
                        + values[i].parameterId()
                        + " does not exist in compute program "
                        + source.id());
            }
        }
        ShaderGraph specialized = ShaderGraph.builder(
                        graph.id().value(), graph.kind())
                .formatVersion(graph.formatVersion())
                .parameters(parameters)
                .resources(graph.resources())
                .nodes(graph.nodes())
                .edges(graph.edges())
                .outputs(graph.outputs())
                .dependencies(graph.dependencies())
                .build();
        return ShaderGraphComputeProgram.builder(
                        source.id().value(), specialized)
                .entryPoint(source.entryPoint())
                .workgroupSize(source.workgroupX(), source.workgroupY(),
                        source.workgroupZ())
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
            T[] values, String label) {
        if (values == null) {
            throw new FdxException(
                    "Compute variant " + label + " cannot be null");
        }
        T[] result = values.clone();
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null || i > 0
                    && result[i - 1].compareTo(result[i]) == 0) {
                throw new FdxException(
                        "Compute variant has duplicate/null " + label);
            }
        }
        return result;
    }

    /**
     * Mutable compute-variant construction scope.
     */
    public static final class Builder {
        private final String key;
        private final ShaderGraphComputeProgram program;
        private ShaderGraphStaticValue[] staticValues =
                new ShaderGraphStaticValue[0];
        private ShaderProfile[] profiles = new ShaderProfile[0];
        private GraphicsFeature[] features = new GraphicsFeature[0];
        private String fallbackKey;

        private Builder(String key, ShaderGraphComputeProgram program) {
            this.key = key;
            this.program = program;
        }

        public Builder staticValues(ShaderGraphStaticValue... values) {
            staticValues = values != null ? values
                    : new ShaderGraphStaticValue[0];
            return this;
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

        public ShaderGraphComputeVariant build() {
            return new ShaderGraphComputeVariant(this);
        }
    }
}
