package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Optional UI metadata stored separately from shader semantics.
 */
public final class ShaderGraphEditorData {
    /** Position of the editor's surface output in graph coordinates. */
    public record Position(float x, float y) {
        public Position {
            if (!Float.isFinite(x) || !Float.isFinite(y)) throw new FdxException("Invalid material output position");
        }

    }

    private final Position materialOutput;
    private final Map<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute> routes;
    private final ShaderGraphId graphId;
    private final ShaderGraphEditorNode[] nodes;
    private final float panX;
    private final float panY;
    private final float zoom;

    private ShaderGraphEditorData(ShaderGraphId graphId,
            ShaderGraphEditorNode[] nodes, float panX, float panY, float zoom, Position materialOutput,
            Map<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute> routes) {
        this.materialOutput = materialOutput;
        this.routes = routes;
        if (graphId == null || nodes == null || !Float.isFinite(panX)
                || !Float.isFinite(panY) || !Float.isFinite(zoom) || zoom <= 0) {
            throw new FdxException("Shader graph editor data is invalid");
        }
        this.graphId = graphId;
        this.nodes = nodes.clone();
        Arrays.sort(this.nodes);
        for (int i = 0; i < this.nodes.length; i++) {
            if (this.nodes[i] == null || i > 0
                    && this.nodes[i - 1].nodeId().equals(this.nodes[i].nodeId())) {
                throw new FdxException("Shader graph editor nodes must have unique IDs");
            }
        }
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
    }

    public static ShaderGraphEditorData of(String graphId,
            ShaderGraphEditorNode[] nodes, float panX, float panY, float zoom) {
        return new ShaderGraphEditorData(ShaderGraphId.of(graphId), nodes,
                panX, panY, zoom, null, Map.of());
    }

    public ShaderGraphId graphId() {
        return graphId;
    }

    public ShaderGraphEditorNode[] nodes() {
        return nodes.clone();
    }

    /** Returns the stored output position, or null if this layout has none. */
    public Position materialOutput() {
        return materialOutput;
    }

    public ShaderGraphEditorData withMaterialOutput(Position position) {
        if (Objects.equals(materialOutput, position)) return this;
        return new ShaderGraphEditorData(graphId, nodes, panX, panY, zoom, position, routes);
    }

    public ShaderGraphEditorData withNodes(ShaderGraphEditorNode[] nodes) {
        return new ShaderGraphEditorData(graphId, nodes, panX, panY, zoom, materialOutput, routes);
    }

    public ShaderGraphEditorData withViewport(float x, float y, float scale) {
        return new ShaderGraphEditorData(graphId, nodes, x, y, scale, materialOutput, routes);
    }

    /** Returns the immutable map of custom wire routes for this graph. */
    public Map<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute> routes() {
        return routes;
    }

    public ShaderGraphEditorData withRoutes(Map<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute> values) {
        if (routes.equals(values)) return this;
        return new ShaderGraphEditorData(graphId, nodes, panX, panY, zoom, materialOutput, Map.copyOf(values));
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    public float zoom() {
        return zoom;
    }

    /** Compares the graph ID, node layout, material output position and wire routes, ignoring pan and zoom. */
    public boolean hasSameLayout(ShaderGraphEditorData other) {
        return this == other || other != null && graphId.equals(other.graphId)
                && Objects.equals(materialOutput, other.materialOutput) && routes.equals(other.routes)
                && Arrays.equals(nodes, other.nodes);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorData other
                && hasSameLayout(other)
                && Float.floatToRawIntBits(panX)
                        == Float.floatToRawIntBits(other.panX)
                && Float.floatToRawIntBits(panY)
                        == Float.floatToRawIntBits(other.panY)
                && Float.floatToRawIntBits(zoom)
                        == Float.floatToRawIntBits(other.zoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, materialOutput, routes, Arrays.hashCode(nodes),
                Float.floatToRawIntBits(panX),
                Float.floatToRawIntBits(panY),
                Float.floatToRawIntBits(zoom));
    }
}
