package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable editor-only layout for every graph embedded in one document.
 */
public final class ShaderGraphEditorLayout {
    private static final float DEFAULT_NODE_WIDTH = 180.0f;
    private static final float DEFAULT_NODE_HEIGHT = 104.0f;
    private static final float DEFAULT_COLUMN_GAP = 56.0f;
    private static final float DEFAULT_ROW_GAP = 44.0f;
    private final ShaderGraphEditorData[] graphs;
    private final String activeGraphId;

    private ShaderGraphEditorLayout(ShaderGraphEditorData[] graphs, String activeGraphId) {
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
        return new ShaderGraphEditorLayout(graphs, activeGraphId);
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
                values.length > 0 ? values[0].graphId().value() : "");
    }

    public ShaderGraphEditorData[] graphs() {
        return graphs.clone();
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
        return new ShaderGraphEditorLayout(graphs, graphId);
    }

    public ShaderGraphEditorLayout withViewport(String graphId, float panX, float panY, float zoom) {
        ShaderGraphEditorData current = requireGraph(graphId);
        return withGraph(ShaderGraphEditorData.of(graphId, current.nodes(), panX, panY, zoom));
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
                return withGraph(ShaderGraphEditorData.of(graphId, nodes,
                        current.panX(), current.panY(), current.zoom()));
            }
        }
        ShaderGraphEditorNode[] expanded = Arrays.copyOf(nodes, nodes.length + 1);
        expanded[nodes.length] = node;
        return withGraph(ShaderGraphEditorData.of(graphId, expanded,
                current.panX(), current.panY(), current.zoom()));
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
        return withGraph(ShaderGraphEditorData.of(graphId, reduced,
                current.panX(), current.panY(), current.zoom()));
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
            values[i] = reconcileGraph(graph, current);
        }
        String active = document.graph(activeGraphId) != null
                ? activeGraphId
                : values.length > 0 ? values[0].graphId().value() : "";
        ShaderGraphEditorLayout result = new ShaderGraphEditorLayout(values, active);
        return equals(result) ? this : result;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorLayout other
                && activeGraphId.equals(other.activeGraphId)
                && Arrays.equals(graphs, other.graphs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activeGraphId, Arrays.hashCode(graphs));
    }

    private ShaderGraphEditorLayout withGraph(ShaderGraphEditorData value) {
        ShaderGraphEditorData[] next = graphs.clone();
        for (int i = 0; i < next.length; i++) {
            if (next[i].graphId().equals(value.graphId())) {
                next[i] = value;
                return new ShaderGraphEditorLayout(next, activeGraphId);
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
        for (int i = 0; i < nodes.length; i++) {
            ShaderGraphEditorNode retained = current != null
                    ? node(current.nodes(), nodes[i].id()) : null;
            values[i] = retained != null ? retained : defaultNode(i, nodes[i].id());
        }
        return ShaderGraphEditorData.of(graph.id().value(), values,
                current != null ? current.panX() : 32.0f,
                current != null ? current.panY() : 32.0f,
                current != null ? current.zoom() : 1.0f);
    }

    private static ShaderGraphEditorData defaultGraphLayout(ShaderGraph graph) {
        ShaderNode[] nodes = graph.nodes();
        ShaderGraphEditorNode[] values = new ShaderGraphEditorNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            values[i] = defaultNode(i, nodes[i].id());
        }
        return ShaderGraphEditorData.of(graph.id().value(), values, 32.0f, 32.0f, 1.0f);
    }

    private static ShaderGraphEditorNode defaultNode(int index, ShaderGraphId id) {
        int column = index % 4;
        int row = index / 4;
        return ShaderGraphEditorNode.of(id.value(),
                column * (DEFAULT_NODE_WIDTH + DEFAULT_COLUMN_GAP),
                row * (DEFAULT_NODE_HEIGHT + DEFAULT_ROW_GAP),
                DEFAULT_NODE_WIDTH, DEFAULT_NODE_HEIGHT, false);
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
