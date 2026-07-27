package io.github.libfdx.graphics.shadergraph.node;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOpcode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderStage;

import java.util.Arrays;

/**
 * Immutable explicitly composed node-definition registry.
 */
public final class ShaderNodeRegistry {
    private static final ShaderGraphKind[] EXPRESSION_KINDS = {
            ShaderGraphKind.FUNCTION, ShaderGraphKind.SUBGRAPH,
            ShaderGraphKind.SURFACE, ShaderGraphKind.VERTEX,
            ShaderGraphKind.FRAGMENT, ShaderGraphKind.COMPUTE
    };
    private static final ShaderStage[] ALL_STAGES = ShaderStage.values();
    private static final ShaderNodeRegistry STANDARD = standardBuilder().build();

    private final ShaderNodeDefinition[] definitions;

    private ShaderNodeRegistry(Builder builder) {
        definitions = builder.definitions.clone();
        Arrays.sort(definitions);
        for (int i = 0; i < definitions.length; i++) {
            if (definitions[i] == null || i > 0
                    && definitions[i - 1].compareTo(definitions[i]) == 0) {
                throw new FdxException("Shader node definitions must have unique ID/version pairs");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ShaderNodeRegistry standard() {
        return STANDARD;
    }

    public ShaderNodeDefinition definition(ShaderGraphId id, int version) {
        for (ShaderNodeDefinition definition : definitions) {
            if (definition.id().equals(id) && definition.version() == version) {
                return definition;
            }
        }
        return null;
    }

    public ShaderNodeDefinition[] definitions() {
        return definitions.clone();
    }

    private static Builder standardBuilder() {
        Builder builder = builder();
        add(builder, StandardShaderNodes.CONSTANT, ShaderIrOpcode.CONSTANT, true);
        add(builder, StandardShaderNodes.PARAMETER, ShaderIrOpcode.PARAMETER, true);
        add(builder, StandardShaderNodes.RESOURCE, ShaderIrOpcode.RESOURCE, true);
        add(builder, StandardShaderNodes.ADD, ShaderIrOpcode.ADD, true);
        add(builder, StandardShaderNodes.SUBTRACT, ShaderIrOpcode.SUBTRACT, true);
        add(builder, StandardShaderNodes.MULTIPLY, ShaderIrOpcode.MULTIPLY, true);
        add(builder, StandardShaderNodes.DIVIDE, ShaderIrOpcode.DIVIDE, true);
        add(builder, StandardShaderNodes.MINIMUM, ShaderIrOpcode.MINIMUM, true);
        add(builder, StandardShaderNodes.MAXIMUM, ShaderIrOpcode.MAXIMUM, true);
        add(builder, StandardShaderNodes.NEGATE, ShaderIrOpcode.NEGATE, true);
        add(builder, StandardShaderNodes.ABSOLUTE, ShaderIrOpcode.ABSOLUTE, true);
        add(builder, StandardShaderNodes.NORMALIZE, ShaderIrOpcode.NORMALIZE, true);
        add(builder, StandardShaderNodes.DOT, ShaderIrOpcode.DOT, true);
        add(builder, StandardShaderNodes.CROSS, ShaderIrOpcode.CROSS, true);
        add(builder, StandardShaderNodes.CLAMP, ShaderIrOpcode.CLAMP, true);
        add(builder, StandardShaderNodes.LERP, ShaderIrOpcode.LERP, true);
        add(builder, StandardShaderNodes.CONSTRUCT, ShaderIrOpcode.CONSTRUCT, true);
        add(builder, StandardShaderNodes.CONVERT, ShaderIrOpcode.CONVERT, true);
        add(builder, StandardShaderNodes.MEMBER, ShaderIrOpcode.MEMBER, true);
        add(builder, StandardShaderNodes.BRANCH, ShaderIrOpcode.BRANCH, true);
        add(builder, StandardShaderNodes.SWITCH, ShaderIrOpcode.SWITCH, true);
        add(builder, StandardShaderNodes.LOOP, ShaderIrOpcode.LOOP, true);
        add(builder, StandardShaderNodes.TEXTURE_SAMPLE,
                ShaderIrOpcode.TEXTURE_SAMPLE, true);
        add(builder, StandardShaderNodes.FUNCTION_CALL,
                ShaderIrOpcode.FUNCTION_CALL, true);
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.DERIVATIVE_X,
                1, ShaderIrOpcode.DERIVATIVE_X, true, null,
                new ShaderStage[] { ShaderStage.FRAGMENT },
                ShaderGraphKind.FUNCTION, ShaderGraphKind.SUBGRAPH,
                ShaderGraphKind.SURFACE, ShaderGraphKind.FRAGMENT));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.DERIVATIVE_Y,
                1, ShaderIrOpcode.DERIVATIVE_Y, true, null,
                new ShaderStage[] { ShaderStage.FRAGMENT },
                ShaderGraphKind.FUNCTION, ShaderGraphKind.SUBGRAPH,
                ShaderGraphKind.SURFACE, ShaderGraphKind.FRAGMENT));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.DISCARD,
                1, ShaderIrOpcode.DISCARD, true, null,
                new ShaderStage[] { ShaderStage.FRAGMENT },
                ShaderGraphKind.FRAGMENT));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.CUSTOM_FUNCTION,
                1, ShaderIrOpcode.CUSTOM_FUNCTION, false, null,
                ALL_STAGES, ShaderGraphKind.FUNCTION, ShaderGraphKind.VERTEX,
                ShaderGraphKind.FRAGMENT, ShaderGraphKind.COMPUTE));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.ATOMIC_ADD,
                1, ShaderIrOpcode.ATOMIC_ADD, false, GraphicsFeature.ATOMICS,
                new ShaderStage[] { ShaderStage.COMPUTE }, ShaderGraphKind.COMPUTE));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.STORAGE_LOAD,
                1, ShaderIrOpcode.STORAGE_LOAD, false, null,
                new ShaderStage[] { ShaderStage.COMPUTE }, ShaderGraphKind.COMPUTE));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.STORAGE_STORE,
                1, ShaderIrOpcode.STORAGE_STORE, false, null,
                new ShaderStage[] { ShaderStage.COMPUTE }, ShaderGraphKind.COMPUTE));
        builder.definition(ShaderNodeDefinition.of(StandardShaderNodes.BARRIER,
                1, ShaderIrOpcode.BARRIER, false, GraphicsFeature.COMPUTE,
                new ShaderStage[] { ShaderStage.COMPUTE }, ShaderGraphKind.COMPUTE));
        return builder;
    }

    private static void add(Builder builder, String id, ShaderIrOpcode opcode,
            boolean webgl2) {
        builder.definition(ShaderNodeDefinition.of(id, 1, opcode, webgl2,
                null, ALL_STAGES, EXPRESSION_KINDS));
    }

    /**
     * Mutable composition scope.
     */
    public static final class Builder {
        private ShaderNodeDefinition[] definitions = new ShaderNodeDefinition[0];

        public Builder definition(ShaderNodeDefinition value) {
            if (value == null) {
                throw new FdxException("Shader node definition cannot be null");
            }
            ShaderNodeDefinition[] next =
                    Arrays.copyOf(definitions, definitions.length + 1);
            next[definitions.length] = value;
            definitions = next;
            return this;
        }

        public ShaderNodeRegistry build() {
            return new ShaderNodeRegistry(this);
        }
    }
}
