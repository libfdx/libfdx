package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.collections.Array;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;

/**
 * Headless Java authoring API for the same semantic model used by serialized
 * graph assets.
 */
public final class ShaderGraphBuilder {
    private static final ShaderGraphId VALUE_PORT = ShaderGraphId.of("value");

    private final ShaderGraphId graphId;
    private final ShaderGraphKind kind;
    private final Array<ShaderGraphParameter> parameters = new Array<ShaderGraphParameter>();
    private final Array<ShaderGraphResource> resources = new Array<ShaderGraphResource>();
    private final Array<ShaderNode> nodes = new Array<ShaderNode>();
    private final Array<ShaderEdge> edges = new Array<ShaderEdge>();
    private final Array<ShaderGraphOutput> outputs = new Array<ShaderGraphOutput>();
    private final Array<ShaderGraphDependency> dependencies = new Array<ShaderGraphDependency>();
    private int nextId;

    public ShaderGraphBuilder(String graphId, ShaderGraphKind kind) {
        this.graphId = ShaderGraphId.of(graphId);
        if (kind == null) {
            throw new FdxException("Shader graph builder kind cannot be null");
        }
        this.kind = kind;
    }

    public ShaderGraphBuilder parameter(ShaderGraphParameter value) {
        requireOpen();
        parameters.add(value);
        return this;
    }

    public ShaderGraphBuilder resource(ShaderGraphResource value) {
        requireOpen();
        resources.add(value);
        return this;
    }

    public ShaderExpression constant(String nodeId, ShaderGraphLiteral value) {
        return node(nodeId, StandardShaderNodes.CONSTANT, value.type(),
                new ShaderGraphPort[0], new ShaderExpression[0],
                ShaderNodeProperty.literal("literal", value));
    }

    public ShaderExpression floatValue(float value) {
        return constant(autoId("constant"), ShaderGraphLiteral.f32(value));
    }

    public ShaderExpression parameter(String nodeId, String parameterId) {
        ShaderGraphParameter parameter = parameter(ShaderGraphId.of(parameterId));
        if (parameter == null) {
            throw new FdxException("Unknown shader graph parameter: " + parameterId);
        }
        return node(nodeId, StandardShaderNodes.PARAMETER, parameter.type(),
                new ShaderGraphPort[0], new ShaderExpression[0],
                ShaderNodeProperty.string("parameter", parameter.id().value()));
    }

    public ShaderExpression resource(String nodeId, String resourceId) {
        ShaderGraphResource resource = resource(ShaderGraphId.of(resourceId));
        if (resource == null) {
            throw new FdxException("Unknown shader graph resource: " + resourceId);
        }
        return node(nodeId, StandardShaderNodes.RESOURCE, resource.type(),
                new ShaderGraphPort[0], new ShaderExpression[0],
                ShaderNodeProperty.string("resource", resource.id().value()));
    }

    public ShaderExpression add(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.ADD, left, right, left.type());
    }

    public ShaderExpression subtract(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.SUBTRACT, left, right, left.type());
    }

    public ShaderExpression multiply(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.MULTIPLY, left, right, left.type());
    }

    public ShaderExpression divide(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.DIVIDE, left, right, left.type());
    }

    public ShaderExpression minimum(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.MINIMUM, left, right, left.type());
    }

    public ShaderExpression maximum(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.MAXIMUM, left, right, left.type());
    }

    public ShaderExpression negate(String nodeId, ShaderExpression value) {
        return unary(nodeId, StandardShaderNodes.NEGATE, value, value.type());
    }

    public ShaderExpression absolute(String nodeId, ShaderExpression value) {
        return unary(nodeId, StandardShaderNodes.ABSOLUTE, value, value.type());
    }

    public ShaderExpression normalize(String nodeId, ShaderExpression value) {
        return unary(nodeId, StandardShaderNodes.NORMALIZE, value, value.type());
    }

    public ShaderExpression dot(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.DOT, left, right,
                ShaderGraphType.scalar(left.type().valueType().scalarType()));
    }

    public ShaderExpression cross(String nodeId, ShaderExpression left,
            ShaderExpression right) {
        return binary(nodeId, StandardShaderNodes.CROSS, left, right, left.type());
    }

    public ShaderExpression clamp(String nodeId, ShaderExpression value,
            ShaderExpression low, ShaderExpression high) {
        return node(nodeId, StandardShaderNodes.CLAMP, value.type(),
                ports(value, low, high), new ShaderExpression[] { value, low, high });
    }

    public ShaderExpression lerp(String nodeId, ShaderExpression left,
            ShaderExpression right, ShaderExpression amount) {
        return node(nodeId, StandardShaderNodes.LERP, left.type(),
                ports(left, right, amount),
                new ShaderExpression[] { left, right, amount });
    }

    public ShaderExpression construct(String nodeId, ShaderGraphType type,
            ShaderExpression... values) {
        return node(nodeId, StandardShaderNodes.CONSTRUCT, type, ports(values),
                values, ShaderNodeProperty.type("type", type));
    }

    public ShaderExpression convert(String nodeId, ShaderExpression value,
            ShaderGraphType type) {
        return node(nodeId, StandardShaderNodes.CONVERT, type,
                ports(value), new ShaderExpression[] { value },
                ShaderNodeProperty.type("type", type));
    }

    public ShaderExpression member(String nodeId, ShaderExpression value,
            String memberId, ShaderGraphType type) {
        return node(nodeId, StandardShaderNodes.MEMBER, type,
                ports(value), new ShaderExpression[] { value },
                ShaderNodeProperty.string("member", memberId));
    }

    public ShaderExpression branch(String nodeId, ShaderExpression condition,
            ShaderExpression whenTrue, ShaderExpression whenFalse) {
        return node(nodeId, StandardShaderNodes.BRANCH, whenTrue.type(),
                new ShaderGraphPort[] {
                        ShaderGraphPort.required(portId(0), condition.type()),
                        ShaderGraphPort.required(portId(1), whenTrue.type()),
                        ShaderGraphPort.required(portId(2), whenFalse.type())
                }, new ShaderExpression[] { condition, whenTrue, whenFalse });
    }

    public ShaderExpression switchValue(String nodeId, ShaderExpression selector,
            ShaderExpression defaultValue, long[] caseValues,
            ShaderExpression... caseResults) {
        if (caseValues == null || caseResults == null
                || caseValues.length != caseResults.length) {
            throw new FdxException("Shader switch cases and values must have equal lengths");
        }
        ShaderExpression[] values = new ShaderExpression[caseResults.length + 2];
        values[0] = selector;
        values[1] = defaultValue;
        System.arraycopy(caseResults, 0, values, 2, caseResults.length);
        return node(nodeId, StandardShaderNodes.SWITCH, defaultValue.type(),
                ports(values), values,
                ShaderNodeProperty.integers("cases", caseValues));
    }

    public ShaderExpression loop(String nodeId, ShaderExpression initial,
            ShaderExpression step, int iterations) {
        return node(nodeId, StandardShaderNodes.LOOP, initial.type(),
                ports(initial, step), new ShaderExpression[] { initial, step },
                ShaderNodeProperty.integer("iterations", iterations));
    }

    public ShaderExpression sample2D(String nodeId, ShaderExpression texture,
            ShaderExpression sampler, ShaderExpression uv) {
        return node(nodeId, StandardShaderNodes.TEXTURE_SAMPLE,
                ShaderGraphType.vector(ShaderScalarType.F32, 4),
                ports(texture, sampler, uv),
                new ShaderExpression[] { texture, sampler, uv });
    }

    public ShaderExpression call(String nodeId, ShaderGraph function,
            ShaderExpression... arguments) {
        if (function == null || function.outputs().length != 1) {
            throw new FdxException("Phase 4 graph calls require a function with one output");
        }
        ShaderGraphParameter[] inputs = function.parameters();
        if (inputs.length != arguments.length) {
            throw new FdxException("Shader function argument count does not match: "
                    + function.id());
        }
        ShaderGraphPort[] ports = new ShaderGraphPort[arguments.length];
        for (int i = 0; i < ports.length; i++) {
            ports[i] = ShaderGraphPort.required(portId(i), inputs[i].type());
        }
        addDependency(function);
        return node(nodeId, StandardShaderNodes.FUNCTION_CALL,
                function.outputs()[0].type(), ports, arguments,
                ShaderNodeProperty.string("function", function.id().value()));
    }

    public ShaderExpression derivativeX(String nodeId, ShaderExpression value) {
        return unary(nodeId, StandardShaderNodes.DERIVATIVE_X, value, value.type());
    }

    public ShaderExpression derivativeY(String nodeId, ShaderExpression value) {
        return unary(nodeId, StandardShaderNodes.DERIVATIVE_Y, value, value.type());
    }

    /**
     * Adds a controlled WGSL expression node. The expression can reference
     * declared inputs as {@code $0}, {@code $1}, and so on. Declarations,
     * statements, attributes, and undeclared identifiers are rejected by the
     * compiler.
     */
    public ShaderExpression customWgsl(String nodeId, ShaderGraphType type,
            String expression, ShaderExpression... inputs) {
        return node(nodeId, StandardShaderNodes.CUSTOM_FUNCTION, type,
                ports(inputs), inputs,
                ShaderNodeProperty.string("body", expression));
    }

    /**
     * Discards the current fragment when the condition is true and returns the
     * condition so the operation remains part of the typed value graph.
     */
    public ShaderExpression discardIf(String nodeId,
            ShaderExpression condition) {
        if (condition == null || !condition.type().isBoolean()) {
            throw new FdxException("Shader discard condition must be boolean");
        }
        return node(nodeId, StandardShaderNodes.DISCARD, condition.type(),
                ports(condition), new ShaderExpression[] { condition });
    }

    /**
     * Loads one element or texel from a compute storage/workgroup resource.
     */
    public ShaderExpression storageLoad(String nodeId,
            ShaderExpression resource, ShaderExpression index,
            ShaderExpression... dependency) {
        requireCompute();
        requireResourceExpression(resource, "Storage load");
        if (dependency != null && dependency.length > 1) {
            throw new FdxException(
                    "Storage load accepts at most one ordering dependency");
        }
        ShaderGraphType outputType = loadedType(resource.type());
        ShaderExpression[] inputs = dependency != null
                && dependency.length == 1
                        ? new ShaderExpression[] {
                                resource, index, dependency[0]
                        }
                        : new ShaderExpression[] { resource, index };
        return node(nodeId, StandardShaderNodes.STORAGE_LOAD, outputType,
                ports(inputs), inputs);
    }

    /**
     * Stores one element or texel and returns the stored value as an ordering
     * token for later side effects.
     */
    public ShaderExpression storageStore(String nodeId,
            ShaderExpression resource, ShaderExpression index,
            ShaderExpression value, ShaderExpression... dependency) {
        requireCompute();
        requireResourceExpression(resource, "Storage store");
        if (dependency != null && dependency.length > 1) {
            throw new FdxException(
                    "Storage store accepts at most one ordering dependency");
        }
        ShaderExpression[] inputs = dependency != null
                && dependency.length == 1
                        ? new ShaderExpression[] {
                                resource, index, value, dependency[0]
                        }
                        : new ShaderExpression[] { resource, index, value };
        return node(nodeId, StandardShaderNodes.STORAGE_STORE, value.type(),
                ports(inputs), inputs);
    }

    /**
     * Atomically adds a scalar to one atomic storage/workgroup element and
     * returns its prior value.
     */
    public ShaderExpression atomicAdd(String nodeId,
            ShaderExpression resource, ShaderExpression index,
            ShaderExpression value) {
        requireCompute();
        requireResourceExpression(resource, "Atomic add");
        return node(nodeId, StandardShaderNodes.ATOMIC_ADD, value.type(),
                ports(resource, index, value),
                new ShaderExpression[] { resource, index, value });
    }

    /**
     * Inserts a compute memory barrier after {@code dependency} and returns
     * that value as an ordering token.
     */
    public ShaderExpression barrier(String nodeId,
            ShaderGraphBarrierScope scope, ShaderExpression dependency) {
        requireCompute();
        if (scope == null || dependency == null) {
            throw new FdxException(
                    "Compute barrier requires a scope and dependency");
        }
        return node(nodeId, StandardShaderNodes.BARRIER, dependency.type(),
                ports(dependency), new ShaderExpression[] { dependency },
                ShaderNodeProperty.string("scope",
                        scope.name().toLowerCase()));
    }

    public ShaderGraphBuilder output(String id, ShaderExpression value) {
        return output(id, "", value);
    }

    public ShaderGraphBuilder output(String id, String semantic,
            ShaderExpression value) {
        if (value == null) {
            throw new FdxException("Shader graph output expression cannot be null");
        }
        outputs.add(ShaderGraphOutput.semantic(id, value.type(),
                value.endpoint(), semantic));
        return this;
    }

    public ShaderGraph build() {
        return ShaderGraph.builder(graphId.value(), kind)
                .parameters(parameters.toArray(new ShaderGraphParameter[0]))
                .resources(resources.toArray(new ShaderGraphResource[0]))
                .nodes(nodes.toArray(new ShaderNode[0]))
                .edges(edges.toArray(new ShaderEdge[0]))
                .outputs(outputs.toArray(new ShaderGraphOutput[0]))
                .dependencies(dependencies.toArray(new ShaderGraphDependency[0]))
                .build();
    }

    private ShaderExpression unary(String nodeId, String definition,
            ShaderExpression value, ShaderGraphType outputType) {
        return node(nodeId, definition, outputType, ports(value),
                new ShaderExpression[] { value });
    }

    private ShaderExpression binary(String nodeId, String definition,
            ShaderExpression left, ShaderExpression right,
            ShaderGraphType outputType) {
        return node(nodeId, definition, outputType, ports(left, right),
                new ShaderExpression[] { left, right });
    }

    private ShaderExpression node(String nodeId, String definition,
            ShaderGraphType outputType, ShaderGraphPort[] inputPorts,
            ShaderExpression[] inputs, ShaderNodeProperty... properties) {
        requireOpen();
        if (nodeId == null || outputType == null || inputPorts.length != inputs.length) {
            throw new FdxException("Shader graph node construction is invalid");
        }
        ShaderNode node = ShaderNode.of(nodeId, definition, 1, inputPorts,
                new ShaderGraphPort[] {
                        ShaderGraphPort.required(VALUE_PORT.value(), outputType)
                }, properties);
        nodes.add(node);
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) {
                throw new FdxException("Shader graph node input cannot be null");
            }
            edges.add(ShaderEdge.of(inputs[i].endpoint(),
                    ShaderEndpoint.of(node.id(), inputPorts[i].id())));
        }
        return new ShaderExpression(ShaderEndpoint.of(node.id(), VALUE_PORT),
                outputType);
    }

    private static ShaderGraphPort[] ports(ShaderExpression... values) {
        ShaderGraphPort[] result = new ShaderGraphPort[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new FdxException("Shader graph expression cannot be null");
            }
            result[i] = ShaderGraphPort.required(portId(i), values[i].type());
        }
        return result;
    }

    private static String portId(int index) {
        String digits = Integer.toString(index);
        return "in" + "0".repeat(Math.max(0, 6 - digits.length())) + digits;
    }

    private ShaderGraphParameter parameter(ShaderGraphId id) {
        for (ShaderGraphParameter parameter : parameters) {
            if (parameter.id().equals(id)) {
                return parameter;
            }
        }
        return null;
    }

    private ShaderGraphResource resource(ShaderGraphId id) {
        for (ShaderGraphResource resource : resources) {
            if (resource.id().equals(id)) {
                return resource;
            }
        }
        return null;
    }

    private void addDependency(ShaderGraph graph) {
        for (ShaderGraphDependency dependency : dependencies) {
            if (dependency.graphId().equals(graph.id())) {
                if (!dependency.semanticHash().equals(graph.semanticHash())) {
                    throw new FdxException("Conflicting shader graph dependency: " + graph.id());
                }
                return;
            }
        }
        dependencies.add(ShaderGraphDependency.of(graph.id().value(),
                graph.semanticHash()));
    }

    private String autoId(String prefix) {
        return prefix + '_' + nextId++;
    }

    private void requireOpen() {
        // Kept as a method so a future sealing builder can preserve behavior
        // without changing call sites.
    }

    private void requireCompute() {
        if (kind != ShaderGraphKind.COMPUTE) {
            throw new FdxException(
                    "Compute operations require a compute graph");
        }
    }

    private static void requireResourceExpression(
            ShaderExpression expression, String operation) {
        if (expression == null
                || expression.type().kind()
                        != ShaderGraphTypeKind.STORAGE_BUFFER
                        && expression.type().kind()
                                != ShaderGraphTypeKind.STORAGE_TEXTURE
                        && expression.type().kind()
                                != ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            throw new FdxException(operation
                    + " requires a storage or workgroup resource");
        }
    }

    private static ShaderGraphType loadedType(ShaderGraphType resource) {
        if (resource.kind() == ShaderGraphTypeKind.STORAGE_TEXTURE) {
            return resource.storageTextureTexelType();
        }
        ShaderGraphType element = resource.elementType();
        if (element.kind() == ShaderGraphTypeKind.VALUE
                && element.valueType().kind()
                        == io.github.libfdx.graphics.shader.reflection.ShaderValueKind.ATOMIC) {
            return ShaderGraphType.scalar(
                    element.valueType().scalarType());
        }
        return element;
    }
}
