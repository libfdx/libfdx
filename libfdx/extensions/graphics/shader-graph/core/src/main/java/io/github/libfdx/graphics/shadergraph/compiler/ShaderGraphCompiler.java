package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrFunction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrInstruction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOpcode;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOutput;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrValue;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBarrierScope;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphDependency;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphFormat;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderStructField;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeDefinition;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodePropertyKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeRegistry;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provider-neutral semantic validator and typed graph-to-IR compiler.
 */
public final class ShaderGraphCompiler {
    private final ShaderNodeRegistry registry;

    public ShaderGraphCompiler() {
        this(ShaderNodeRegistry.standard());
    }

    public ShaderGraphCompiler(ShaderNodeRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Shader node registry cannot be null");
        }
        this.registry = registry;
    }

    public ShaderGraphCompileResult compile(ShaderGraph graph,
            ShaderGraphCompileOptions options) {
        if (graph == null) {
            throw new IllegalArgumentException("Shader graph cannot be null");
        }
        ShaderGraphCompileOptions actual = options != null ? options
                : ShaderGraphCompileOptions.builder().build();
        Compilation compilation = new Compilation(actual);
        compilation.visit(graph, new ShaderGraphId[0]);
        compilation.sortDiagnostics();
        if (compilation.hasErrors()) {
            return new ShaderGraphCompileResult(null, "", "", graph.semanticHash(),
                    compilation.diagnostics(), new ShaderSourceSpan[0]);
        }
        ShaderIrModule module = new ShaderIrModule(
                compilation.functions.toArray(ShaderIrFunction[]::new));
        ShaderWgslEmitter.Result emitted = new ShaderWgslEmitter().emit(module);
        ShaderWgslEmitter.Result library =
                new ShaderWgslEmitter().emitLibrary(module);
        return new ShaderGraphCompileResult(module, emitted.source(),
                library.source(),
                graph.semanticHash(), compilation.diagnostics(),
                emitted.sourceMap());
    }

    private final class Compilation {
        private final ShaderGraphCompileOptions options;
        private final List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        private final List<ShaderIrFunction> functions = new ArrayList<>();
        private final List<ShaderGraphId> completed = new ArrayList<>();

        Compilation(ShaderGraphCompileOptions options) {
            this.options = options;
        }

        void visit(ShaderGraph graph, ShaderGraphId[] stack) {
            if (contains(completed, graph.id())) {
                return;
            }
            if (contains(stack, graph.id())) {
                error(graph, "FDXG_RECURSION",
                        "Recursive shader graph dependency: " + graph.id(),
                        null, null);
                return;
            }
            ShaderGraphId[] nextStack = Arrays.copyOf(stack, stack.length + 1);
            nextStack[stack.length] = graph.id();
            validateFormatAndProfile(graph);
            for (ShaderGraphDependency dependency : graph.dependencies()) {
                ShaderGraph child = options.library().resolve(dependency.graphId());
                if (child == null) {
                    error(graph, "FDXG_DEPENDENCY_MISSING",
                            "Missing shader graph dependency: " + dependency.graphId(),
                            null, null);
                    continue;
                }
                if (!dependency.semanticHash().equals(child.semanticHash())) {
                    error(graph, "FDXG_DEPENDENCY_HASH",
                            "Shader graph dependency hash is stale: "
                                    + dependency.graphId(),
                            null, null);
                    continue;
                }
                visit(child, nextStack);
            }
            GraphState state = validateGraph(graph);
            if (!state.valid) {
                return;
            }
            ShaderIrFunction function = lower(graph, state);
            functions.add(function);
            completed.add(graph.id());
        }

        private void validateFormatAndProfile(ShaderGraph graph) {
            if (graph.formatVersion() != ShaderGraphFormat.CURRENT_VERSION) {
                error(graph, "FDXG_FORMAT_VERSION",
                        "Unsupported shader graph format version "
                                + graph.formatVersion() + "; expected "
                                + ShaderGraphFormat.CURRENT_VERSION,
                        null, null);
            }
            GraphicsCapabilities capabilities = options.capabilities();
            if (capabilities != null && !capabilities.supports(options.profile())) {
                error(graph, "FDXG_PROFILE_UNSUPPORTED",
                        "Graphics capabilities do not support profile "
                                + options.profile(),
                        null, null);
            }
            ShaderStage stage = effectiveStage(graph);
            if (options.profile() == ShaderProfile.PORTABLE_WEBGL2
                    && stage == ShaderStage.COMPUTE) {
                error(graph, "FDXG_PROFILE_COMPUTE",
                        "The WebGL2 shader profile does not support compute graphs",
                        null, null);
            }
        }

        private GraphState validateGraph(ShaderGraph graph) {
            GraphState state = new GraphState(graph);
            if (graph.outputs().length == 0) {
                error(graph, "FDXG_OUTPUT_MISSING",
                        "Shader graph requires at least one output", null, null);
            }
            validateBindings(graph);
            for (ShaderNode node : graph.nodes()) {
                ShaderNodeDefinition definition = registry.definition(
                        node.definitionId(), node.definitionVersion());
                state.definitions[state.nodeIndex(node.id())] = definition;
                if (definition == null) {
                    error(graph, "FDXG_NODE_VERSION",
                            "Unknown shader node definition/version "
                                    + node.definitionId() + '@'
                                    + node.definitionVersion(),
                            node.id(), null);
                    continue;
                }
                if (!definition.supports(graph.kind())) {
                    error(graph, "FDXG_NODE_DOMAIN",
                            "Node " + node.definitionId()
                                    + " is not legal in " + graph.kind(),
                            node.id(), null);
                }
                ShaderStage stage = effectiveStage(graph);
                if (!definition.supports(stage)) {
                    error(graph, "FDXG_NODE_STAGE",
                            "Node " + node.definitionId()
                                    + " is not legal in stage " + stage,
                            node.id(), null);
                }
                if (options.profile() == ShaderProfile.PORTABLE_WEBGL2
                        && !definition.supportsWebGl2()) {
                    error(graph, "FDXG_NODE_PROFILE",
                            "Node " + node.definitionId()
                                    + " is not legal in the WebGL2 profile",
                            node.id(), null);
                }
                GraphicsFeature feature = definition.requiredFeature();
                if (feature != null && options.capabilities() != null
                        && !options.capabilities().supports(feature)) {
                    error(graph, "FDXG_NODE_CAPABILITY",
                            "Node " + node.definitionId()
                                    + " requires graphics feature " + feature,
                            node.id(), null);
                }
                if (node.outputs().length != 1) {
                    error(graph, "FDXG_NODE_OUTPUT_COUNT",
                            "Graph expression nodes require exactly one output",
                            node.id(), null);
                }
            }
            validateEdges(graph, state);
            validateOutputs(graph);
            state.topological = topological(graph, state);
            for (ShaderNode node : graph.nodes()) {
                ShaderNodeDefinition definition =
                        state.definitions[state.nodeIndex(node.id())];
                if (definition != null) {
                    validateOperation(graph, node, definition.opcode(), state);
                }
            }
            state.valid = !hasErrorsFor(graph.id());
            return state;
        }

        private void validateBindings(ShaderGraph graph) {
            ShaderGraphResource[] resources = graph.resources();
            int storageBuffers = 0;
            int storageTextures = 0;
            long workgroupBytes = 0;
            for (int i = 0; i < resources.length; i++) {
                for (int j = 0; j < i; j++) {
                    if (resources[i].bound() && resources[j].bound()
                            && resources[i].group() == resources[j].group()
                            && resources[i].binding() == resources[j].binding()) {
                        error(graph, "FDXG_RESOURCE_BINDING",
                                "Duplicate graph resource binding @group("
                                        + resources[i].group() + ") @binding("
                                        + resources[i].binding() + ')',
                                null, null);
                    }
                }
                ShaderGraphResource resource = resources[i];
                ShaderGraphTypeKind kind = resource.type().kind();
                if ((kind == ShaderGraphTypeKind.STORAGE_BUFFER
                        || kind == ShaderGraphTypeKind.STORAGE_TEXTURE
                        || kind == ShaderGraphTypeKind.WORKGROUP_ARRAY)
                        && graph.kind() != ShaderGraphKind.COMPUTE) {
                    error(graph, "FDXG_RESOURCE_STAGE",
                            "Storage and workgroup resources require a compute graph",
                            null, null);
                }
                if (kind == ShaderGraphTypeKind.STORAGE_BUFFER) {
                    storageBuffers++;
                    requireFeature(graph, GraphicsFeature.STORAGE_BUFFERS,
                            "Storage-buffer graph resource");
                } else if (kind == ShaderGraphTypeKind.STORAGE_TEXTURE) {
                    storageTextures++;
                    requireFeature(graph, GraphicsFeature.STORAGE_TEXTURES,
                            "Storage-texture graph resource");
                } else if (kind == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
                    workgroupBytes += workgroupStorageSize(resource.type());
                }
                GraphicsCapabilities capabilities = options.capabilities();
                if (resource.bound() && capabilities != null) {
                    GraphicsLimits limits = capabilities.limits();
                    if (resource.group() >= limits.maxBindGroups()
                            || resource.binding()
                                    >= limits.maxBindingsPerGroup()) {
                        error(graph, "FDXG_RESOURCE_LIMIT",
                                "Graph resource binding exceeds provider limits: "
                                        + resource.id(),
                                null, null);
                    }
                }
            }
            GraphicsCapabilities capabilities = options.capabilities();
            if (capabilities != null) {
                GraphicsLimits limits = capabilities.limits();
                if (storageBuffers > limits.maxStorageBuffersPerStage()) {
                    error(graph, "FDXG_RESOURCE_LIMIT",
                            "Compute graph storage-buffer count exceeds the provider limit",
                            null, null);
                }
                if (storageTextures > limits.maxStorageTexturesPerStage()) {
                    error(graph, "FDXG_RESOURCE_LIMIT",
                            "Compute graph storage-texture count exceeds the provider limit",
                            null, null);
                }
                if (workgroupBytes
                        > limits.maxComputeWorkgroupStorageSize()) {
                    error(graph, "FDXG_WORKGROUP_STORAGE_LIMIT",
                            "Compute graph workgroup storage requires "
                                    + workgroupBytes + " bytes, provider limit is "
                                    + limits.maxComputeWorkgroupStorageSize(),
                            null, null);
                }
            }
        }

        private void requireFeature(ShaderGraph graph,
                GraphicsFeature feature, String label) {
            if (options.capabilities() != null
                    && !options.capabilities().supports(feature)) {
                error(graph, "FDXG_RESOURCE_CAPABILITY",
                        label + " requires graphics feature " + feature,
                        null, null);
            }
        }

        private void validateEdges(ShaderGraph graph, GraphState state) {
            for (ShaderEdge edge : graph.edges()) {
                ShaderNode source = graph.node(edge.source().nodeId());
                ShaderNode target = graph.node(edge.target().nodeId());
                if (source == null) {
                    error(graph, "FDXG_EDGE_SOURCE_NODE",
                            "Edge source node does not exist: " + edge.source(),
                            edge.source().nodeId(), edge.source().portId());
                    continue;
                }
                if (target == null) {
                    error(graph, "FDXG_EDGE_TARGET_NODE",
                            "Edge target node does not exist: " + edge.target(),
                            edge.target().nodeId(), edge.target().portId());
                    continue;
                }
                ShaderGraphPort sourcePort = source.output(edge.source().portId());
                ShaderGraphPort targetPort = target.input(edge.target().portId());
                if (sourcePort == null) {
                    error(graph, "FDXG_EDGE_SOURCE_PORT",
                            "Edge source port does not exist: " + edge.source(),
                            source.id(), edge.source().portId());
                    continue;
                }
                if (targetPort == null) {
                    error(graph, "FDXG_EDGE_TARGET_PORT",
                            "Edge target port does not exist: " + edge.target(),
                            target.id(), edge.target().portId());
                    continue;
                }
                if (!sourcePort.type().equals(targetPort.type())) {
                    error(graph, "FDXG_EDGE_TYPE",
                            "Edge type " + sourcePort.type()
                                    + " does not match input type "
                                    + targetPort.type(),
                            target.id(), targetPort.id());
                }
                int targetIndex = state.inputIndex(target.id(), targetPort.id());
                if (targetIndex >= 0 && state.sources[targetIndex] != null) {
                    error(graph, "FDXG_EDGE_DUPLICATE",
                            "Multiple edges target the same input: " + edge.target(),
                            target.id(), targetPort.id());
                } else if (targetIndex >= 0) {
                    state.sources[targetIndex] = edge.source();
                }
            }
            for (ShaderNode node : graph.nodes()) {
                for (ShaderGraphPort port : node.inputs()) {
                    int index = state.inputIndex(node.id(), port.id());
                    if (port.required() && state.sources[index] == null
                            && port.defaultValue() == null) {
                        error(graph, "FDXG_INPUT_MISSING",
                                "Required node input is not connected",
                                node.id(), port.id());
                    }
                }
            }
        }

        private void validateOutputs(ShaderGraph graph) {
            for (ShaderGraphOutput output : graph.outputs()) {
                ShaderNode node = graph.node(output.source().nodeId());
                ShaderGraphPort port = node != null
                        ? node.output(output.source().portId()) : null;
                if (port == null) {
                    error(graph, "FDXG_OUTPUT_SOURCE",
                            "Graph output source does not exist: " + output.source(),
                            output.source().nodeId(), output.source().portId());
                } else if (!port.type().equals(output.type())) {
                    error(graph, "FDXG_OUTPUT_TYPE",
                            "Graph output type does not match its source",
                            node.id(), port.id());
                }
            }
        }

        private ShaderNode[] topological(ShaderGraph graph, GraphState state) {
            ShaderNode[] nodes = graph.nodes();
            int[] indegree = new int[nodes.length];
            for (ShaderEdge edge : graph.edges()) {
                int source = state.nodeIndex(edge.source().nodeId());
                int target = state.nodeIndex(edge.target().nodeId());
                if (source >= 0 && target >= 0 && source != target) {
                    indegree[target]++;
                } else if (source == target && source >= 0) {
                    indegree[target]++;
                }
            }
            ShaderNode[] result = new ShaderNode[nodes.length];
            boolean[] emitted = new boolean[nodes.length];
            for (int output = 0; output < result.length; output++) {
                int selected = -1;
                for (int i = 0; i < nodes.length; i++) {
                    if (!emitted[i] && indegree[i] == 0) {
                        selected = i;
                        break;
                    }
                }
                if (selected < 0) {
                    error(graph, "FDXG_CYCLE",
                            "Shader graph contains a value cycle", null, null);
                    return new ShaderNode[0];
                }
                emitted[selected] = true;
                result[output] = nodes[selected];
                for (ShaderEdge edge : graph.edges()) {
                    if (edge.source().nodeId().equals(nodes[selected].id())) {
                        int target = state.nodeIndex(edge.target().nodeId());
                        if (target >= 0) {
                            indegree[target]--;
                        }
                    }
                }
            }
            return result;
        }

        private void validateOperation(ShaderGraph graph, ShaderNode node,
                ShaderIrOpcode opcode, GraphState state) {
            ShaderGraphPort[] inputs = node.inputs();
            ShaderGraphType output = node.outputs()[0].type();
            switch (opcode) {
                case CONSTANT -> {
                    ShaderNodeProperty literal = requireProperty(graph, node,
                            "literal", ShaderNodePropertyKind.LITERAL);
                    if (literal != null && !literal.literalValue().type().equals(output)) {
                        operationError(graph, node,
                                "Constant literal and output types differ");
                    }
                    requireInputCount(graph, node, 0);
                }
                case PARAMETER -> {
                    ShaderNodeProperty property = requireProperty(graph, node,
                            "parameter", ShaderNodePropertyKind.STRING);
                    ShaderGraphParameter parameter = property != null
                            ? graph.parameter(ShaderGraphId.of(property.stringValue()))
                            : null;
                    if (parameter == null) {
                        operationError(graph, node,
                                "Parameter node references an unknown parameter");
                    } else if (!parameter.type().equals(output)) {
                        operationError(graph, node,
                                "Parameter node output type does not match its declaration");
                    }
                    requireInputCount(graph, node, 0);
                }
                case RESOURCE -> {
                    ShaderNodeProperty property = requireProperty(graph, node,
                            "resource", ShaderNodePropertyKind.STRING);
                    ShaderGraphResource resource = property != null
                            ? graph.resource(ShaderGraphId.of(property.stringValue()))
                            : null;
                    if (resource == null) {
                        operationError(graph, node,
                                "Resource node references an unknown resource");
                    } else if (!resource.type().equals(output)) {
                        operationError(graph, node,
                                "Resource node output type does not match its declaration");
                    }
                    requireInputCount(graph, node, 0);
                }
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, MINIMUM, MAXIMUM -> {
                    requireInputCount(graph, node, 2);
                    requireSameNumeric(graph, node, output);
                }
                case NEGATE, ABSOLUTE -> {
                    requireInputCount(graph, node, 1);
                    requireSameNumeric(graph, node, output);
                }
                case NORMALIZE -> {
                    requireInputCount(graph, node, 1);
                    requireSameNumeric(graph, node, output);
                    if (!isFloatVector(output)) {
                        operationError(graph, node,
                                "Normalize requires a floating vector");
                    }
                }
                case DOT -> {
                    requireInputCount(graph, node, 2);
                    if (inputs.length == 2
                            && (!inputs[0].type().equals(inputs[1].type())
                            || !isFloatVector(inputs[0].type())
                            || output.kind() != ShaderGraphTypeKind.VALUE
                            || output.valueType().kind() != ShaderValueKind.SCALAR
                            || output.valueType().scalarType()
                                    != inputs[0].type().valueType().scalarType())) {
                        operationError(graph, node,
                                "Dot requires equal floating vectors and a scalar output");
                    }
                }
                case CROSS -> {
                    requireInputCount(graph, node, 2);
                    requireSameNumeric(graph, node, output);
                    if (!isFloatVector(output)
                            || output.valueType().componentCount() != 3) {
                        operationError(graph, node,
                                "Cross requires three-component floating vectors");
                    }
                }
                case CLAMP, LERP -> {
                    requireInputCount(graph, node, 3);
                    requireSameNumeric(graph, node, output);
                }
                case CONSTRUCT -> validateConstruct(graph, node, output);
                case CONVERT -> {
                    requireInputCount(graph, node, 1);
                    requireProperty(graph, node, "type", ShaderNodePropertyKind.TYPE);
                    if (!output.isNumeric() || inputs.length == 1
                            && !inputs[0].type().isNumeric()) {
                        operationError(graph, node,
                                "Conversion requires numeric input and output");
                    }
                }
                case MEMBER -> validateMember(graph, node, output);
                case BRANCH -> {
                    requireInputCount(graph, node, 3);
                    if (inputs.length == 3 && (!inputs[0].type().isBoolean()
                            || !inputs[1].type().equals(output)
                            || !inputs[2].type().equals(output))) {
                        operationError(graph, node,
                                "Branch requires bool, true value, and false value");
                    }
                }
                case SWITCH -> validateSwitch(graph, node, output);
                case LOOP -> validateLoop(graph, node, output);
                case TEXTURE_SAMPLE -> validateTextureSample(graph, node, output);
                case FUNCTION_CALL -> validateCall(graph, node, output);
                case DERIVATIVE_X, DERIVATIVE_Y -> {
                    requireInputCount(graph, node, 1);
                    requireSameNumeric(graph, node, output);
                    if (!isFloat(output)) {
                        operationError(graph, node,
                                "Derivatives require floating values");
                    }
                }
                case CUSTOM_FUNCTION -> {
                    ShaderNodeProperty body = requireProperty(graph, node, "body",
                            ShaderNodePropertyKind.STRING);
                    if (body != null) {
                        try {
                            ShaderCustomWgslExpression.normalize(
                                    body.stringValue(), inputs.length);
                        } catch (IllegalArgumentException failure) {
                            operationError(graph, node, failure.getMessage());
                        }
                    }
                }
                case DISCARD -> {
                    requireInputCount(graph, node, 1);
                    if (inputs.length == 1
                            && (!inputs[0].type().isBoolean()
                                    || !output.isBoolean())) {
                        operationError(graph, node,
                                "Discard requires and returns a boolean condition");
                    }
                }
                case ATOMIC_ADD -> validateAtomicAdd(graph, node, output);
                case STORAGE_LOAD -> validateStorageLoad(
                        graph, node, output);
                case STORAGE_STORE -> validateStorageStore(
                        graph, node, output);
                case BARRIER -> validateBarrier(graph, node, output);
            }
        }

        private void validateStorageLoad(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            if (node.inputs().length != 2
                    && node.inputs().length != 3) {
                operationError(graph, node,
                        "Storage load requires resource, index, and optional dependency");
                return;
            }
            ShaderGraphType resource = node.inputs()[0].type();
            if (!storageResource(resource)) {
                operationError(graph, node,
                        "Storage load requires a storage/workgroup resource");
                return;
            }
            if (!readable(resource.resourceAccess())) {
                operationError(graph, node,
                        "Storage load requires readable resource access");
            }
            validateStorageIndex(graph, node, resource,
                    node.inputs()[1].type());
            ShaderGraphType expected = loadedType(resource);
            if (!expected.equals(output)) {
                operationError(graph, node,
                        "Storage load output type does not match the resource element");
            }
        }

        private void validateStorageStore(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            if (node.inputs().length != 3
                    && node.inputs().length != 4) {
                operationError(graph, node,
                        "Storage store requires resource, index, value, and optional dependency");
                return;
            }
            ShaderGraphType resource = node.inputs()[0].type();
            if (!storageResource(resource)) {
                operationError(graph, node,
                        "Storage store requires a storage/workgroup resource");
                return;
            }
            if (!writable(resource.resourceAccess())) {
                operationError(graph, node,
                        "Storage store requires writable resource access");
            }
            validateStorageIndex(graph, node, resource,
                    node.inputs()[1].type());
            ShaderGraphType value = node.inputs()[2].type();
            ShaderGraphType expected = loadedType(resource);
            if (!expected.equals(value) || !value.equals(output)) {
                operationError(graph, node,
                        "Storage store value/output type does not match the resource element");
            }
        }

        private void validateAtomicAdd(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireInputCount(graph, node, 3);
            if (node.inputs().length != 3) {
                return;
            }
            ShaderGraphType resource = node.inputs()[0].type();
            if (resource.kind() != ShaderGraphTypeKind.STORAGE_BUFFER
                    && resource.kind()
                            != ShaderGraphTypeKind.WORKGROUP_ARRAY) {
                operationError(graph, node,
                        "Atomic add requires a buffer/workgroup resource");
                return;
            }
            ShaderGraphType element = resource.elementType();
            if (element.kind() != ShaderGraphTypeKind.VALUE
                    || element.valueType().kind()
                            != ShaderValueKind.ATOMIC
                    || resource.resourceAccess()
                            != ShaderResourceAccess.READ_WRITE) {
                operationError(graph, node,
                        "Atomic add requires a read-write atomic element resource");
                return;
            }
            validateStorageIndex(graph, node, resource,
                    node.inputs()[1].type());
            ShaderGraphType value = node.inputs()[2].type();
            ShaderGraphType scalar = ShaderGraphType.scalar(
                    element.valueType().scalarType());
            if (!value.equals(scalar) || !output.equals(scalar)) {
                operationError(graph, node,
                        "Atomic add value and output must match the atomic scalar");
            }
        }

        private void validateBarrier(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireInputCount(graph, node, 1);
            ShaderNodeProperty scope = requireProperty(graph, node, "scope",
                    ShaderNodePropertyKind.STRING);
            if (node.inputs().length == 1
                    && !node.inputs()[0].type().equals(output)) {
                operationError(graph, node,
                        "Barrier ordering token type must be preserved");
            }
            if (scope != null) {
                try {
                    ShaderGraphBarrierScope.valueOf(
                            scope.stringValue().trim().toUpperCase());
                } catch (IllegalArgumentException failure) {
                    operationError(graph, node,
                            "Unknown compute barrier scope "
                                    + scope.stringValue());
                }
            }
        }

        private void validateStorageIndex(ShaderGraph graph,
                ShaderNode node, ShaderGraphType resource,
                ShaderGraphType index) {
            if (resource.kind() == ShaderGraphTypeKind.STORAGE_TEXTURE) {
                if (index.kind() != ShaderGraphTypeKind.VALUE
                        || index.valueType().kind()
                                != ShaderValueKind.VECTOR
                        || index.valueType().scalarType()
                                != ShaderScalarType.I32
                        || index.componentCount() != 2) {
                    operationError(graph, node,
                            "2D storage-texture coordinates must be vec2<i32>");
                }
                return;
            }
            if (index.kind() != ShaderGraphTypeKind.VALUE
                    || index.valueType().kind()
                            != ShaderValueKind.SCALAR
                    || index.valueType().scalarType()
                            != ShaderScalarType.I32
                            && index.valueType().scalarType()
                                    != ShaderScalarType.U32) {
                operationError(graph, node,
                        "Storage/workgroup array index must be i32 or u32");
            }
        }

        private void validateConstruct(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireProperty(graph, node, "type", ShaderNodePropertyKind.TYPE);
            int components = 0;
            for (ShaderGraphPort input : node.inputs()) {
                components += Math.max(1, input.type().componentCount());
            }
            if (output.kind() == ShaderGraphTypeKind.STRUCT) {
                if (node.inputs().length != output.structType().fieldCount()) {
                    operationError(graph, node,
                            "Structure construction requires one value per field");
                } else {
                    for (int i = 0; i < node.inputs().length; i++) {
                        if (!node.inputs()[i].type().equals(
                                output.structType().field(i).type())) {
                            operationError(graph, node,
                                    "Structure constructor field type mismatch");
                        }
                    }
                }
            } else if (components != output.componentCount()) {
                operationError(graph, node,
                        "Constructor component count does not match output type");
            }
        }

        private void validateMember(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireInputCount(graph, node, 1);
            ShaderNodeProperty property = requireProperty(graph, node,
                    "member", ShaderNodePropertyKind.STRING);
            if (node.inputs().length == 0 || property == null) {
                return;
            }
            ShaderGraphType input = node.inputs()[0].type();
            if (input.kind() == ShaderGraphTypeKind.STRUCT) {
                ShaderStructField field = input.structType().field(
                        ShaderGraphId.of(property.stringValue()));
                if (field == null || !field.type().equals(output)) {
                    operationError(graph, node,
                            "Structure member does not exist or has the wrong type");
                }
            } else if (input.kind() != ShaderGraphTypeKind.VALUE
                    || input.valueType().kind() != ShaderValueKind.VECTOR
                    || !validSwizzle(property.stringValue(),
                            input.valueType().componentCount())
                    || output.componentCount() != property.stringValue().length()) {
                operationError(graph, node,
                        "Vector member must be a valid xyzw swizzle");
            }
        }

        private void validateSwitch(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            if (node.inputs().length < 2) {
                operationError(graph, node,
                        "Switch requires selector and default inputs");
                return;
            }
            ShaderGraphType selector = node.inputs()[0].type();
            ShaderScalarType scalar = selector.kind() == ShaderGraphTypeKind.VALUE
                    ? selector.valueType().scalarType() : ShaderScalarType.UNKNOWN;
            if (selector.kind() != ShaderGraphTypeKind.VALUE
                    || selector.valueType().kind() != ShaderValueKind.SCALAR
                    || scalar != ShaderScalarType.I32
                            && scalar != ShaderScalarType.U32) {
                operationError(graph, node,
                        "Switch selector must be an i32 or u32 scalar");
            }
            ShaderNodeProperty cases = requireProperty(graph, node, "cases",
                    ShaderNodePropertyKind.INTEGER_LIST);
            if (cases != null && cases.integerValues().length
                    != node.inputs().length - 2) {
                operationError(graph, node,
                        "Switch case labels do not match case inputs");
            }
            for (int i = 1; i < node.inputs().length; i++) {
                if (!node.inputs()[i].type().equals(output)) {
                    operationError(graph, node,
                            "Switch result types must match");
                }
            }
        }

        private void validateLoop(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireInputCount(graph, node, 2);
            ShaderNodeProperty iterations = requireProperty(graph, node,
                    "iterations", ShaderNodePropertyKind.INTEGER);
            if (iterations != null && (iterations.integerValue() < 0
                    || iterations.integerValue() > 1024)) {
                operationError(graph, node,
                        "Loop iteration count must be between 0 and 1024");
            }
            requireSameNumeric(graph, node, output);
        }

        private void validateTextureSample(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            requireInputCount(graph, node, 3);
            if (node.inputs().length != 3) {
                return;
            }
            ShaderGraphType texture = node.inputs()[0].type();
            ShaderGraphType sampler = node.inputs()[1].type();
            ShaderGraphType uv = node.inputs()[2].type();
            if (texture.kind() != ShaderGraphTypeKind.TEXTURE
                    || sampler.kind() != ShaderGraphTypeKind.SAMPLER
                    || !isFloatVector(uv) || uv.componentCount() != 2
                    || !isFloatVector(output) || output.componentCount() != 4) {
                operationError(graph, node,
                        "2D texture sampling requires texture, sampler, vec2 UV, and vec4 output");
            }
        }

        private void validateCall(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            ShaderNodeProperty property = requireProperty(graph, node,
                    "function", ShaderNodePropertyKind.STRING);
            if (property == null) {
                return;
            }
            ShaderGraph child = options.library().resolve(
                    ShaderGraphId.of(property.stringValue()));
            if (child == null) {
                operationError(graph, node,
                        "Function call dependency is missing");
                return;
            }
            if (child.kind() != ShaderGraphKind.FUNCTION
                    && child.kind() != ShaderGraphKind.SUBGRAPH) {
                operationError(graph, node,
                        "Function calls may target only function/subgraph assets");
            }
            if (child.parameters().length != node.inputs().length
                    || child.outputs().length != 1
                    || !child.outputs()[0].type().equals(output)) {
                operationError(graph, node,
                        "Function call signature does not match its dependency");
                return;
            }
            for (int i = 0; i < node.inputs().length; i++) {
                if (!node.inputs()[i].type().equals(child.parameters()[i].type())) {
                    operationError(graph, node,
                            "Function call argument type mismatch");
                }
            }
        }

        private ShaderIrFunction lower(ShaderGraph graph, GraphState state) {
            List<ShaderIrInstruction> instructions = new ArrayList<>();
            List<ShaderIrValue> values = new ArrayList<>();
            for (ShaderNode node : state.topological) {
                ShaderNodeDefinition definition =
                        state.definitions[state.nodeIndex(node.id())];
                List<ShaderIrValue> operands = new ArrayList<>();
                for (ShaderGraphPort input : node.inputs()) {
                    ShaderEndpoint source =
                            state.sources[state.inputIndex(node.id(), input.id())];
                    if (source != null) {
                        operands.add(findValue(values, source));
                    } else {
                        ShaderGraphLiteral literal = input.defaultValue() != null
                                ? input.defaultValue()
                                : ShaderGraphLiteral.zero(input.type());
                        ShaderGraphId defaultId = ShaderGraphId.of(
                                node.id().value() + "__default__" + input.id().value());
                        ShaderIrValue defaultValue = new ShaderIrValue(defaultId,
                                input.type());
                        instructions.add(new ShaderIrInstruction(
                                ShaderIrOpcode.CONSTANT, defaultValue,
                                new ShaderIrValue[0],
                                new ShaderNodeProperty[] {
                                        ShaderNodeProperty.literal("literal", literal)
                                }, graph.id(), node.id(), input.id()));
                        values.add(defaultValue);
                        operands.add(defaultValue);
                    }
                }
                ShaderGraphPort output = node.outputs()[0];
                ShaderIrValue result = new ShaderIrValue(
                        valueId(node.id(), output.id()), output.type());
                instructions.add(new ShaderIrInstruction(definition.opcode(),
                        result, operands.toArray(ShaderIrValue[]::new),
                        node.properties(), graph.id(), node.id(), output.id()));
                values.add(result);
            }
            ShaderIrOutput[] outputs = new ShaderIrOutput[graph.outputs().length];
            for (int i = 0; i < outputs.length; i++) {
                ShaderGraphOutput output = graph.outputs()[i];
                outputs[i] = new ShaderIrOutput(output.id(), output.semantic(),
                        findValue(values, output.source()));
            }
            return new ShaderIrFunction(graph.id(), graph.kind(),
                    graph.parameters(), graph.resources(),
                    instructions.toArray(ShaderIrInstruction[]::new), outputs);
        }

        private ShaderNodeProperty requireProperty(ShaderGraph graph,
                ShaderNode node, String id, ShaderNodePropertyKind kind) {
            ShaderNodeProperty property = node.property(id);
            if (property == null || property.kind() != kind) {
                operationError(graph, node,
                        "Node requires " + kind + " property '" + id + "'");
                return null;
            }
            return property;
        }

        private void requireInputCount(ShaderGraph graph, ShaderNode node,
                int count) {
            if (node.inputs().length != count) {
                operationError(graph, node,
                        "Node requires " + count + " inputs");
            }
        }

        private void requireSameNumeric(ShaderGraph graph, ShaderNode node,
                ShaderGraphType output) {
            if (!output.isNumeric()) {
                operationError(graph, node,
                        "Operation output must be numeric");
                return;
            }
            for (ShaderGraphPort input : node.inputs()) {
                if (!input.type().equals(output)) {
                    operationError(graph, node,
                            "Operation input and output types must match exactly");
                }
            }
        }

        private void operationError(ShaderGraph graph, ShaderNode node,
                String message) {
            error(graph, "FDXG_OPERATION", message, node.id(), null);
        }

        private void error(ShaderGraph graph, String code, String message,
                ShaderGraphId nodeId, ShaderGraphId portId) {
            diagnostics.add(new ShaderGraphDiagnostic(
                    ShaderGraphDiagnosticSeverity.ERROR, code, message,
                    graph.id(), nodeId, portId));
        }

        private boolean hasErrorsFor(ShaderGraphId graphId) {
            for (ShaderGraphDiagnostic diagnostic : diagnostics) {
                if (diagnostic.graphId().equals(graphId)
                        && diagnostic.severity()
                                == ShaderGraphDiagnosticSeverity.ERROR) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasErrors() {
            for (ShaderGraphDiagnostic diagnostic : diagnostics) {
                if (diagnostic.severity() == ShaderGraphDiagnosticSeverity.ERROR) {
                    return true;
                }
            }
            return false;
        }

        private void sortDiagnostics() {
            diagnostics.sort(null);
        }

        private ShaderGraphDiagnostic[] diagnostics() {
            return diagnostics.toArray(ShaderGraphDiagnostic[]::new);
        }

        private ShaderStage effectiveStage(ShaderGraph graph) {
            return switch (graph.kind()) {
                case SURFACE, FRAGMENT -> ShaderStage.FRAGMENT;
                case VERTEX -> ShaderStage.VERTEX;
                case COMPUTE -> ShaderStage.COMPUTE;
                default -> options.stage();
            };
        }
    }

    private static final class GraphState {
        private final ShaderGraph graph;
        private final ShaderNodeDefinition[] definitions;
        private final ShaderGraphId[] inputNodes;
        private final ShaderGraphId[] inputPorts;
        private final ShaderEndpoint[] sources;
        private ShaderNode[] topological = new ShaderNode[0];
        private boolean valid;

        GraphState(ShaderGraph graph) {
            this.graph = graph;
            definitions = new ShaderNodeDefinition[graph.nodes().length];
            int count = 0;
            for (ShaderNode node : graph.nodes()) {
                count += node.inputs().length;
            }
            inputNodes = new ShaderGraphId[count];
            inputPorts = new ShaderGraphId[count];
            sources = new ShaderEndpoint[count];
            int index = 0;
            for (ShaderNode node : graph.nodes()) {
                for (ShaderGraphPort port : node.inputs()) {
                    inputNodes[index] = node.id();
                    inputPorts[index] = port.id();
                    index++;
                }
            }
        }

        int nodeIndex(ShaderGraphId id) {
            ShaderNode[] nodes = graph.nodes();
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].id().equals(id)) {
                    return i;
                }
            }
            return -1;
        }

        int inputIndex(ShaderGraphId node, ShaderGraphId port) {
            for (int i = 0; i < inputNodes.length; i++) {
                if (inputNodes[i].equals(node) && inputPorts[i].equals(port)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static boolean contains(List<ShaderGraphId> values, ShaderGraphId id) {
        for (ShaderGraphId value : values) {
            if (value.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(ShaderGraphId[] values, ShaderGraphId id) {
        for (ShaderGraphId value : values) {
            if (value.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static ShaderIrValue findValue(List<ShaderIrValue> values,
            ShaderEndpoint endpoint) {
        ShaderGraphId id = valueId(endpoint.nodeId(), endpoint.portId());
        for (ShaderIrValue value : values) {
            if (value.id().equals(id)) {
                return value;
            }
        }
        throw new IllegalStateException("Validated shader value was not lowered: "
                + endpoint);
    }

    private static ShaderGraphId valueId(ShaderGraphId node, ShaderGraphId port) {
        return ShaderGraphId.of(node.value() + "__" + port.value());
    }

    private static boolean isFloat(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && (type.valueType().scalarType() == ShaderScalarType.F32
                        || type.valueType().scalarType() == ShaderScalarType.F16);
    }

    private static boolean isFloatVector(ShaderGraphType type) {
        return isFloat(type)
                && type.valueType().kind() == ShaderValueKind.VECTOR;
    }

    private static boolean storageResource(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.STORAGE_BUFFER
                || type.kind() == ShaderGraphTypeKind.STORAGE_TEXTURE
                || type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY;
    }

    private static boolean readable(ShaderResourceAccess access) {
        return access == ShaderResourceAccess.READ
                || access == ShaderResourceAccess.READ_WRITE;
    }

    private static boolean writable(ShaderResourceAccess access) {
        return access == ShaderResourceAccess.WRITE
                || access == ShaderResourceAccess.READ_WRITE;
    }

    private static ShaderGraphType loadedType(ShaderGraphType resource) {
        if (resource.kind() == ShaderGraphTypeKind.STORAGE_TEXTURE) {
            return resource.storageTextureTexelType();
        }
        ShaderGraphType element = resource.elementType();
        if (element.kind() == ShaderGraphTypeKind.VALUE
                && element.valueType().kind() == ShaderValueKind.ATOMIC) {
            return ShaderGraphType.scalar(
                    element.valueType().scalarType());
        }
        return element;
    }

    private static long workgroupStorageSize(ShaderGraphType type) {
        return type.workgroupStorageSize();
    }

    private static boolean validSwizzle(String value, int width) {
        if (value == null || value.isEmpty() || value.length() > 4) {
            return false;
        }
        String allowed = "xyzw".substring(0, width);
        for (int i = 0; i < value.length(); i++) {
            if (allowed.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
