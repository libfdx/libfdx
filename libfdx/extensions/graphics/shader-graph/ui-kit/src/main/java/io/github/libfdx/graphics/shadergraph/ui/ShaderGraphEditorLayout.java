package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute.Wire;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable editor-only layout for every graph embedded in one document.
 */
public final class ShaderGraphEditorLayout {
    private static final float DEFAULT_NODE_WIDTH = 180.0f;
    private static final float DEFAULT_NODE_HEIGHT = 104.0f;
    static final float SOCKET_TOP = 48;
    static final float SOCKET_STEP = 31;
    private static final float DEFAULT_COLUMN_GAP = 56.0f;
    private static final float DEFAULT_ROW_GAP = 44.0f;
    private final ShaderGraphEditorData[] graphs;
    private final String activeGraphId;

    private final ShaderGraphPreviewAnimation previewAnimation;

    private ShaderGraphEditorLayout(ShaderGraphEditorData[] graphs, String activeGraphId, ShaderGraphPreviewAnimation previewAnimation) {
        this.previewAnimation = previewAnimation;
        if (graphs == null) {
            throw new FdxException("Shader graph editor layout graph data cannot be null");
        }
        this.graphs = graphs.clone();
        for (ShaderGraphEditorData graph : this.graphs) {
            if (graph == null) {
                throw new FdxException("Shader graph editor layout graph data cannot contain null");
            }
        }
        Arrays.sort(this.graphs, (left, right) -> left.graphId().compareTo(right.graphId()));
        for (int i = 0; i < this.graphs.length; i++) {
            if (i > 0
                    && this.graphs[i - 1].graphId().equals(this.graphs[i].graphId())) {
                throw new FdxException("Shader graph editor layout graph IDs must be unique");
            }
        }
        String requested = activeGraphId != null ? activeGraphId.trim() : "";
        if (requested.isEmpty() && this.graphs.length > 0) {
            requested = this.graphs[0].graphId().value();
        }
        if (!requested.isEmpty() && graph(requested) == null) {
            throw new FdxException("Shader graph editor active graph has no layout: " + requested);
        }
        this.activeGraphId = requested;
    }

    public static ShaderGraphEditorLayout of(ShaderGraphEditorData[] graphs, String activeGraphId) {
        return new ShaderGraphEditorLayout(graphs, activeGraphId, null);
    }

    public static ShaderGraphEditorLayout forDocument(ShaderGraphEditorDocument document) {
        if (document == null) {
            throw new FdxException("Shader graph editor document cannot be null");
        }
        ShaderGraph[] semanticGraphs = document.graphs();
        ShaderGraphEditorData[] values = new ShaderGraphEditorData[semanticGraphs.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = defaultGraphLayout(semanticGraphs[i]);
        }
        return new ShaderGraphEditorLayout(values,
                values.length > 0 ? values[0].graphId().value() : "", null);
    }

    public ShaderGraphEditorData[] graphs() {
        return graphs.clone();
    }

    public ShaderGraphPreviewAnimation previewAnimation() {
        return previewAnimation;
    }

    public ShaderGraphEditorLayout withPreviewAnimation(ShaderGraphPreviewAnimation animation) {
        return Objects.equals(previewAnimation, animation) ? this : new ShaderGraphEditorLayout(graphs, activeGraphId, animation);
    }

    public String activeGraphId() {
        return activeGraphId;
    }

    public ShaderGraphEditorData activeGraph() {
        return graph(activeGraphId);
    }

    public ShaderGraphEditorData graph(String graphId) {
        if (graphId == null) {
            return null;
        }
        for (ShaderGraphEditorData graph : graphs) {
            if (graph.graphId().value().equals(graphId)) {
                return graph;
            }
        }
        return null;
    }

    public ShaderGraphEditorLayout activeGraph(String graphId) {
        if (graph(graphId) == null) {
            throw new FdxException("Unknown shader graph editor layout " + graphId);
        }
        if (activeGraphId.equals(graphId)) {
            return this;
        }
        return new ShaderGraphEditorLayout(graphs, graphId, previewAnimation);
    }

    public ShaderGraphEditorLayout withViewport(String graphId, float panX, float panY, float zoom) {
        ShaderGraphEditorData current = requireGraph(graphId);
        return withGraph(current.withViewport(panX, panY, zoom));
    }

    ShaderGraphEditorLayout withMaterialOutput(String graphId, float x, float y) {
        return withGraph(requireGraph(graphId).withMaterialOutput(new ShaderGraphEditorData.Position(x, y)));
    }

    ShaderGraphEditorLayout withWireRoute(Wire wire, ShaderGraphWireRoute route) {
        var current = activeGraph();
        var routes = new HashMap<>(current.routes());
        if (route.equals(ShaderGraphWireRoute.DEFAULT)) routes.remove(wire);
        else routes.put(wire, route);
        return withGraph(current.withRoutes(routes));
    }

    ShaderGraphEditorLayout renameWireEndpoints(String graphId, String oldId, String newId) {
        var current = requireGraph(graphId);
        if (current.routes().isEmpty()) return this;
        var renamed = new HashMap<Wire, ShaderGraphWireRoute>();
        var id = ShaderGraphId.of(newId);
        current.routes().forEach((wire, route) -> renamed.put(wire.renameNode(oldId, id), route));
        return withGraph(current.withRoutes(renamed));
    }

    public ShaderGraphEditorLayout withNode(String graphId, ShaderGraphEditorNode node) {
        if (node == null) {
            throw new FdxException("Shader graph editor node layout cannot be null");
        }
        ShaderGraphEditorData current = requireGraph(graphId);
        ShaderGraphEditorNode[] nodes = current.nodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId().equals(node.nodeId())) {
                if (nodes[i].equals(node)) {
                    return this;
                }
                nodes[i] = node;
                return withGraph(current.withNodes(nodes));
            }
        }
        ShaderGraphEditorNode[] expanded = Arrays.copyOf(nodes, nodes.length + 1);
        expanded[nodes.length] = node;
        return withGraph(current.withNodes(expanded));
    }

    public ShaderGraphEditorLayout withoutNode(String graphId, ShaderGraphId nodeId) {
        ShaderGraphEditorData current = requireGraph(graphId);
        ShaderGraphEditorNode[] nodes = current.nodes();
        int found = -1;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeId().equals(nodeId)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            return this;
        }
        ShaderGraphEditorNode[] reduced = new ShaderGraphEditorNode[nodes.length - 1];
        System.arraycopy(nodes, 0, reduced, 0, found);
        System.arraycopy(nodes, found + 1, reduced, found, nodes.length - found - 1);
        return withGraph(current.withNodes(reduced));
    }

    /**
     * Reconciles this layout with semantic graphs. Unknown graph/node metadata
     * is removed and missing metadata receives deterministic default layout.
     *
     * @param document the semantic document
     * @return the reconciled layout
     */
    public ShaderGraphEditorLayout reconcile(ShaderGraphEditorDocument document) {
        ShaderGraph[] semanticGraphs = document.graphs();
        ShaderGraphEditorData[] values = new ShaderGraphEditorData[semanticGraphs.length];
        for (int i = 0; i < semanticGraphs.length; i++) {
            ShaderGraph graph = semanticGraphs[i];
            ShaderGraphEditorData current = graph(graph.id().value());
            values[i] = current == null ? defaultGraphLayout(graph) : reconcileGraph(graph, current);
        }
        String active = document.graph(activeGraphId) != null
                ? activeGraphId
                : values.length > 0 ? values[0].graphId().value() : "";
        ShaderGraphEditorLayout result = new ShaderGraphEditorLayout(values, active,
                previewAnimation != null && previewAnimation.appliesTo(document.graph()) ? previewAnimation : null);
        return equals(result) ? this : result;
    }

    /** Compares authored editor data independently of which graph and viewport are being viewed. */
    boolean hasSameEdits(ShaderGraphEditorLayout other) {
        if (this == other) return true;
        if (!Objects.equals(previewAnimation, other.previewAnimation) || graphs.length != other.graphs.length) return false;
        for (int i = 0; i < graphs.length; i++) {
            if (!graphs[i].hasSameLayout(other.graphs[i])) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorLayout other
                && Objects.equals(previewAnimation, other.previewAnimation)
                && activeGraphId.equals(other.activeGraphId)
                && Arrays.equals(graphs, other.graphs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previewAnimation, activeGraphId, Arrays.hashCode(graphs));
    }

    private ShaderGraphEditorLayout withGraph(ShaderGraphEditorData value) {
        ShaderGraphEditorData[] next = graphs.clone();
        for (int i = 0; i < next.length; i++) {
            if (next[i].graphId().equals(value.graphId())) {
                next[i] = value;
                return new ShaderGraphEditorLayout(next, activeGraphId, previewAnimation);
            }
        }
        throw new FdxException("Unknown shader graph editor layout " + value.graphId());
    }

    private ShaderGraphEditorData requireGraph(String graphId) {
        ShaderGraphEditorData value = graph(graphId);
        if (value == null) {
            throw new FdxException("Unknown shader graph editor layout " + graphId);
        }
        return value;
    }

    private static ShaderGraphEditorData reconcileGraph(ShaderGraph graph, ShaderGraphEditorData current) {
        ShaderNode[] nodes = graph.nodes();
        ShaderGraphEditorNode[] values = new ShaderGraphEditorNode[nodes.length];
        ShaderGraphEditorNode[] existing = current.nodes();
        for (int i = 0; i < nodes.length; i++) {
            ShaderGraphEditorNode retained = node(existing, nodes[i].id());
            values[i] = retained != null ? fitSockets(nodes[i], retained) : defaultNode(i, nodes[i]);
        }
        if (!current.routes().isEmpty()) {
            var retained = new HashMap<Wire, ShaderGraphWireRoute>();
            for (var edge : graph.edges()) retainRoute(current, retained, Wire.of(edge));
            for (var output : graph.outputs()) retainRoute(current, retained, Wire.of(output));
            current = current.withRoutes(retained);
        }
        return placeMaterialOutput(graph, current.withNodes(values), values);
    }

    private static void retainRoute(ShaderGraphEditorData current, Map<Wire, ShaderGraphWireRoute> retained, Wire wire) {
        var route = current.routes().get(wire);
        if (route != null) retained.put(wire, route);
    }

    private static ShaderGraphEditorData defaultGraphLayout(ShaderGraph graph) {
        ShaderNode[] nodes = graph.nodes();
        // Place each operation one column beyond its furthest upstream dependency.
        Map<ShaderGraphId, Integer> indexes = new HashMap<>();
        List<List<Integer>> consumers = new ArrayList<>(nodes.length);
        int[] remaining = new int[nodes.length];
        int[] column = new int[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            indexes.put(nodes[i].id(), i);
            consumers.add(new ArrayList<>());
        }
        for (var edge : graph.edges()) {
            Integer source = indexes.get(edge.source().nodeId());
            Integer target = indexes.get(edge.target().nodeId());
            if (source != null && target != null) {
                consumers.get(source).add(target);
                remaining[target]++;
            }
        }
        var ready = new ArrayDeque<Integer>();
        for (int i = 0; i < nodes.length; i++) {
            if (remaining[i] == 0) {
                ready.addLast(i);
            }
        }
        int lastColumn = 0;
        while (!ready.isEmpty()) {
            int index = ready.removeFirst();
            lastColumn = Math.max(lastColumn, column[index]);
            for (int target : consumers.get(index)) {
                column[target] = Math.max(column[target], column[index] + 1);
                if (--remaining[target] == 0) {
                    ready.addLast(target);
                }
            }
        }
        // Keep unresolvable cycles visible in their own column for repair.
        float[] nextY = new float[lastColumn + 2];
        ShaderGraphEditorNode[] values = new ShaderGraphEditorNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            int x = remaining[i] == 0 ? column[i] : lastColumn + 1;
            values[i] = newNode(nodes[i], x * (DEFAULT_NODE_WIDTH + DEFAULT_COLUMN_GAP), nextY[x]);
            nextY[x] += values[i].height() + DEFAULT_ROW_GAP;
        }
        float zoom = nodes.length > 12 ? 0.75f : 1.0f;
        if (graph.kind() == ShaderGraphKind.SURFACE) zoom = Math.min(zoom, 0.85f);
        var data = placeMaterialOutput(graph, ShaderGraphEditorData.of(graph.id().value(), values, 24, 24, zoom), values);
        var output = data.materialOutput();
        if (output == null) return data;
        // Open a new surface on the final operations. Previously saved viewports are restored in reconcileGraph.
        return data.withViewport(24 - Math.max(0, output.x() - (DEFAULT_NODE_WIDTH + DEFAULT_COLUMN_GAP)) * zoom,
                24 - output.y() * zoom, zoom);
    }

    private static ShaderGraphEditorData placeMaterialOutput(ShaderGraph graph, ShaderGraphEditorData data,
            ShaderGraphEditorNode[] nodes) {
        if (graph.kind() != ShaderGraphKind.SURFACE) return data.withMaterialOutput(null);
        if (data.materialOutput() != null) return data;
        float right = Float.NEGATIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        for (var node : nodes) {
            right = Math.max(right, node.x() + node.width());
            top = Math.min(top, node.y());
        }
        return data.withMaterialOutput(new ShaderGraphEditorData.Position(
                nodes.length == 0 ? 0 : right + DEFAULT_COLUMN_GAP, nodes.length == 0 ? 0 : top));
    }

    private static ShaderGraphEditorNode defaultNode(int index, ShaderNode node) {
        return newNode(node, index % 4 * (DEFAULT_NODE_WIDTH + DEFAULT_COLUMN_GAP),
                index / 4 * (DEFAULT_NODE_HEIGHT + DEFAULT_ROW_GAP));
    }

    static float socketY(int index) {
        return SOCKET_TOP + index * SOCKET_STEP;
    }

    static ShaderGraphEditorNode newNode(ShaderNode node, float x, float y) {
        return fitSockets(node, ShaderGraphEditorNode.of(node.id().value(), x, y, DEFAULT_NODE_WIDTH, DEFAULT_NODE_HEIGHT, false));
    }

    private static ShaderGraphEditorNode fitSockets(ShaderNode node, ShaderGraphEditorNode saved) {
        int rows = Math.max(node.inputs().length, node.outputs().length);
        float height = SOCKET_TOP + (rows - 1) * SOCKET_STEP + 24;
        String definition = node.definitionId().value();
        if (definition.equals(StandardShaderNodes.CONSTANT) || definition.equals(StandardShaderNodes.PARAMETER)) height += 20;
        if (saved.height() >= height) return saved;
        return ShaderGraphEditorNode.of(node.id().value(), saved.x(), saved.y(), saved.width(), height, saved.collapsed());
    }

    private static ShaderGraphEditorNode node(ShaderGraphEditorNode[] nodes, ShaderGraphId id) {
        for (ShaderGraphEditorNode node : nodes) {
            if (node.nodeId().equals(id)) {
                return node;
            }
        }
        return null;
    }
}
