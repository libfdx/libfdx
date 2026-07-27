package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphDependency;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import java.util.Arrays;

/**
 * Immutable semantic graph edit operations used by command history and
 * non-visual editor integrations.
 */
public final class ShaderGraphSemanticEdits {
    private ShaderGraphSemanticEdits() {
    }

    public static ShaderGraph addNode(ShaderGraph graph, ShaderNode node) {
        require(graph, "graph");
        require(node, "node");
        ShaderNode[] nodes = graph.nodes();
        ShaderNode[] expanded = Arrays.copyOf(nodes, nodes.length + 1);
        expanded[nodes.length] = node;
        return rebuild(graph, graph.parameters(), graph.resources(), expanded,
                graph.edges(), graph.outputs(), graph.dependencies());
    }

    public static ShaderGraph duplicateNode(ShaderGraph graph, String nodeId, String duplicateId) {
        ShaderNode source = requireNode(graph, nodeId);
        return addNode(graph, ShaderNode.of(duplicateId, source.definitionId().value(),
                source.definitionVersion(), source.inputs(), source.outputs(), source.properties()));
    }

    public static ShaderGraph removeNodes(ShaderGraph graph, String... nodeIds) {
        require(graph, "graph");
        ShaderGraphId[] removed = ids(nodeIds);
        ShaderNode[] sourceNodes = graph.nodes();
        int nodeCount = 0;
        for (ShaderNode node : sourceNodes) {
            if (!contains(removed, node.id())) {
                nodeCount++;
            }
        }
        if (nodeCount == sourceNodes.length) {
            return graph;
        }
        ShaderNode[] nodes = new ShaderNode[nodeCount];
        int nodeIndex = 0;
        for (ShaderNode node : sourceNodes) {
            if (!contains(removed, node.id())) {
                nodes[nodeIndex++] = node;
            }
        }

        ShaderEdge[] sourceEdges = graph.edges();
        int edgeCount = 0;
        for (ShaderEdge edge : sourceEdges) {
            if (!contains(removed, edge.source().nodeId())
                    && !contains(removed, edge.target().nodeId())) {
                edgeCount++;
            }
        }
        ShaderEdge[] edges = new ShaderEdge[edgeCount];
        int edgeIndex = 0;
        for (ShaderEdge edge : sourceEdges) {
            if (!contains(removed, edge.source().nodeId())
                    && !contains(removed, edge.target().nodeId())) {
                edges[edgeIndex++] = edge;
            }
        }

        ShaderGraphOutput[] sourceOutputs = graph.outputs();
        int outputCount = 0;
        for (ShaderGraphOutput output : sourceOutputs) {
            if (!contains(removed, output.source().nodeId())) {
                outputCount++;
            }
        }
        ShaderGraphOutput[] outputs = new ShaderGraphOutput[outputCount];
        int outputIndex = 0;
        for (ShaderGraphOutput output : sourceOutputs) {
            if (!contains(removed, output.source().nodeId())) {
                outputs[outputIndex++] = output;
            }
        }
        return rebuild(graph, graph.parameters(), graph.resources(), nodes,
                edges, outputs, graph.dependencies());
    }

    /**
     * Connects one output to one input. Any previous connection to the target
     * input is replaced atomically.
     */
    public static ShaderGraph connect(ShaderGraph graph, ShaderEndpoint source, ShaderEndpoint target) {
        require(graph, "graph");
        require(source, "source endpoint");
        require(target, "target endpoint");
        ShaderGraphPort output = requireOutput(graph, source);
        ShaderGraphPort input = requireInput(graph, target);
        if (!output.type().equals(input.type())) {
            throw new FdxException("Cannot connect shader ports with different types: "
                    + source + " -> " + target);
        }
        ShaderEdge connection = ShaderEdge.of(source, target);
        ShaderEdge[] existing = graph.edges();
        int retained = 0;
        boolean same = false;
        for (ShaderEdge edge : existing) {
            if (edge.target().equals(target)) {
                same |= edge.equals(connection);
            } else {
                retained++;
            }
        }
        if (same && retained == existing.length - 1) {
            return graph;
        }
        ShaderEdge[] edges = new ShaderEdge[retained + 1];
        int index = 0;
        for (ShaderEdge edge : existing) {
            if (!edge.target().equals(target)) {
                edges[index++] = edge;
            }
        }
        edges[index] = connection;
        return rebuild(graph, graph.parameters(), graph.resources(), graph.nodes(),
                edges, graph.outputs(), graph.dependencies());
    }

    public static ShaderGraph disconnect(ShaderGraph graph, ShaderEndpoint target) {
        require(graph, "graph");
        require(target, "target endpoint");
        ShaderEdge[] existing = graph.edges();
        int retained = 0;
        for (ShaderEdge edge : existing) {
            if (!edge.target().equals(target)) {
                retained++;
            }
        }
        if (retained == existing.length) {
            return graph;
        }
        ShaderEdge[] edges = new ShaderEdge[retained];
        int index = 0;
        for (ShaderEdge edge : existing) {
            if (!edge.target().equals(target)) {
                edges[index++] = edge;
            }
        }
        return rebuild(graph, graph.parameters(), graph.resources(), graph.nodes(),
                edges, graph.outputs(), graph.dependencies());
    }

    public static ShaderGraph setNodeProperty(ShaderGraph graph, String nodeId,
            ShaderNodeProperty property) {
        require(property, "node property");
        ShaderNode current = requireNode(graph, nodeId);
        ShaderNodeProperty[] properties = current.properties();
        boolean replaced = false;
        for (int i = 0; i < properties.length; i++) {
            if (properties[i].id().equals(property.id())) {
                if (properties[i].equals(property)) {
                    return graph;
                }
                properties[i] = property;
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            properties = Arrays.copyOf(properties, properties.length + 1);
            properties[properties.length - 1] = property;
        }
        ShaderNode replacement = ShaderNode.of(current.id().value(),
                current.definitionId().value(), current.definitionVersion(),
                current.inputs(), current.outputs(), properties);
        return replaceNode(graph, replacement);
    }

    public static ShaderGraph renameNode(ShaderGraph graph, String nodeId, String replacementId) {
        ShaderNode current = requireNode(graph, nodeId);
        ShaderGraphId oldId = current.id();
        ShaderGraphId newId = ShaderGraphId.of(replacementId);
        if (oldId.equals(newId)) {
            return graph;
        }
        if (graph.node(newId) != null) {
            throw new FdxException("Shader graph already contains node " + newId);
        }
        ShaderNode[] nodes = graph.nodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].id().equals(oldId)) {
                nodes[i] = ShaderNode.of(newId.value(), current.definitionId().value(),
                        current.definitionVersion(), current.inputs(), current.outputs(),
                        current.properties());
                break;
            }
        }
        ShaderEdge[] edges = graph.edges();
        for (int i = 0; i < edges.length; i++) {
            ShaderEndpoint source = rename(edges[i].source(), oldId, newId);
            ShaderEndpoint target = rename(edges[i].target(), oldId, newId);
            if (source != edges[i].source() || target != edges[i].target()) {
                edges[i] = ShaderEdge.of(source, target);
            }
        }
        ShaderGraphOutput[] outputs = graph.outputs();
        for (int i = 0; i < outputs.length; i++) {
            ShaderGraphOutput output = outputs[i];
            ShaderEndpoint source = rename(output.source(), oldId, newId);
            if (source != output.source()) {
                outputs[i] = ShaderGraphOutput.semantic(output.id().value(), output.type(),
                        source, output.semantic());
            }
        }
        return rebuild(graph, graph.parameters(), graph.resources(), nodes,
                edges, outputs, graph.dependencies());
    }

    public static ShaderGraph parameters(ShaderGraph graph, ShaderGraphParameter... parameters) {
        return rebuild(graph, parameters, graph.resources(), graph.nodes(),
                graph.edges(), graph.outputs(), graph.dependencies());
    }

    public static ShaderGraph resources(ShaderGraph graph, ShaderGraphResource... resources) {
        return rebuild(graph, graph.parameters(), resources, graph.nodes(),
                graph.edges(), graph.outputs(), graph.dependencies());
    }

    public static ShaderGraph outputs(ShaderGraph graph, ShaderGraphOutput... outputs) {
        return rebuild(graph, graph.parameters(), graph.resources(), graph.nodes(),
                graph.edges(), outputs, graph.dependencies());
    }

    public static ShaderGraph dependencies(ShaderGraph graph, ShaderGraphDependency... dependencies) {
        return rebuild(graph, graph.parameters(), graph.resources(), graph.nodes(),
                graph.edges(), graph.outputs(), dependencies);
    }

    private static ShaderGraph replaceNode(ShaderGraph graph, ShaderNode replacement) {
        ShaderNode[] nodes = graph.nodes();
        boolean found = false;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].id().equals(replacement.id())) {
                nodes[i] = replacement;
                found = true;
                break;
            }
        }
        if (!found) {
            throw new FdxException("Shader graph does not contain node " + replacement.id());
        }
        return rebuild(graph, graph.parameters(), graph.resources(), nodes,
                graph.edges(), graph.outputs(), graph.dependencies());
    }

    private static ShaderGraph rebuild(ShaderGraph graph,
            ShaderGraphParameter[] parameters, ShaderGraphResource[] resources,
            ShaderNode[] nodes, ShaderEdge[] edges, ShaderGraphOutput[] outputs,
            ShaderGraphDependency[] dependencies) {
        return ShaderGraph.builder(graph.id().value(), graph.kind())
                .formatVersion(graph.formatVersion())
                .parameters(parameters)
                .resources(resources)
                .nodes(nodes)
                .edges(edges)
                .outputs(outputs)
                .dependencies(dependencies)
                .build();
    }

    private static ShaderNode requireNode(ShaderGraph graph, String nodeId) {
        require(graph, "graph");
        ShaderNode node = graph.node(ShaderGraphId.of(nodeId));
        if (node == null) {
            throw new FdxException("Shader graph does not contain node " + nodeId);
        }
        return node;
    }

    private static ShaderGraphPort requireOutput(ShaderGraph graph, ShaderEndpoint endpoint) {
        ShaderNode node = requireNode(graph, endpoint.nodeId().value());
        ShaderGraphPort port = node.output(endpoint.portId());
        if (port == null) {
            throw new FdxException("Shader node has no output " + endpoint);
        }
        return port;
    }

    private static ShaderGraphPort requireInput(ShaderGraph graph, ShaderEndpoint endpoint) {
        ShaderNode node = requireNode(graph, endpoint.nodeId().value());
        ShaderGraphPort port = node.input(endpoint.portId());
        if (port == null) {
            throw new FdxException("Shader node has no input " + endpoint);
        }
        return port;
    }

    private static ShaderEndpoint rename(ShaderEndpoint endpoint, ShaderGraphId oldId,
            ShaderGraphId newId) {
        return endpoint.nodeId().equals(oldId)
                ? ShaderEndpoint.of(newId, endpoint.portId()) : endpoint;
    }

    private static ShaderGraphId[] ids(String[] values) {
        if (values == null || values.length == 0) {
            throw new FdxException("Shader graph node removal requires at least one node ID");
        }
        ShaderGraphId[] result = new ShaderGraphId[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = ShaderGraphId.of(values[i]);
        }
        return result;
    }

    private static boolean contains(ShaderGraphId[] values, ShaderGraphId value) {
        for (ShaderGraphId item : values) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new FdxException("Shader graph editor " + label + " cannot be null");
        }
    }
}
