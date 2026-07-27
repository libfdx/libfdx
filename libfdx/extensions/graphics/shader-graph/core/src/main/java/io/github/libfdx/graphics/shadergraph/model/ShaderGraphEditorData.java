package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Optional UI metadata stored separately from shader semantics.
 */
public final class ShaderGraphEditorData {
    private final ShaderGraphId graphId;
    private final ShaderGraphEditorNode[] nodes;
    private final float panX;
    private final float panY;
    private final float zoom;

    private ShaderGraphEditorData(ShaderGraphId graphId,
            ShaderGraphEditorNode[] nodes, float panX, float panY, float zoom) {
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
                panX, panY, zoom);
    }

    public ShaderGraphId graphId() {
        return graphId;
    }

    public ShaderGraphEditorNode[] nodes() {
        return nodes.clone();
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

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorData other
                && graphId.equals(other.graphId)
                && Arrays.equals(nodes, other.nodes)
                && Float.floatToRawIntBits(panX)
                        == Float.floatToRawIntBits(other.panX)
                && Float.floatToRawIntBits(panY)
                        == Float.floatToRawIntBits(other.panY)
                && Float.floatToRawIntBits(zoom)
                        == Float.floatToRawIntBits(other.zoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, Arrays.hashCode(nodes),
                Float.floatToRawIntBits(panX),
                Float.floatToRawIntBits(panY),
                Float.floatToRawIntBits(zoom));
    }
}
