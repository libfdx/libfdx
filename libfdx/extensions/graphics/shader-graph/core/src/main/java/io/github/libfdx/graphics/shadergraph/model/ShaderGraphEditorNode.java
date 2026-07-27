package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Optional editor-only layout for one semantic node.
 */
public final class ShaderGraphEditorNode implements Comparable<ShaderGraphEditorNode> {
    private final ShaderGraphId nodeId;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final boolean collapsed;

    private ShaderGraphEditorNode(ShaderGraphId nodeId, float x, float y,
            float width, float height, boolean collapsed) {
        if (nodeId == null || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0 || height < 0) {
            throw new FdxException("Shader graph editor node has invalid layout");
        }
        this.nodeId = nodeId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.collapsed = collapsed;
    }

    public static ShaderGraphEditorNode of(String nodeId, float x, float y,
            float width, float height, boolean collapsed) {
        return new ShaderGraphEditorNode(ShaderGraphId.of(nodeId), x, y,
                width, height, collapsed);
    }

    public ShaderGraphId nodeId() {
        return nodeId;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public boolean collapsed() {
        return collapsed;
    }

    @Override
    public int compareTo(ShaderGraphEditorNode other) {
        return nodeId.compareTo(other.nodeId);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorNode other
                && nodeId.equals(other.nodeId)
                && Float.floatToRawIntBits(x) == Float.floatToRawIntBits(other.x)
                && Float.floatToRawIntBits(y) == Float.floatToRawIntBits(other.y)
                && Float.floatToRawIntBits(width) == Float.floatToRawIntBits(other.width)
                && Float.floatToRawIntBits(height) == Float.floatToRawIntBits(other.height)
                && collapsed == other.collapsed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, Float.floatToRawIntBits(x),
                Float.floatToRawIntBits(y), Float.floatToRawIntBits(width),
                Float.floatToRawIntBits(height), collapsed);
    }
}
