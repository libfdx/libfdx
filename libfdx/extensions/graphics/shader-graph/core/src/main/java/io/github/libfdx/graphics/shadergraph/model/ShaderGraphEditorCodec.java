package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Codec for optional editor-only sidecar data.
 */
public final class ShaderGraphEditorCodec {
    private ShaderGraphEditorCodec() {
    }

    public static String write(ShaderGraphEditorData data) {
        if (data == null) {
            throw new FdxException("Shader graph editor data cannot be null");
        }
        JsonValue nodes = JsonValue.array();
        for (ShaderGraphEditorNode node : data.nodes()) {
            nodes.add(JsonValue.object()
                    .put("id", node.nodeId().value())
                    .put("x", node.x())
                    .put("y", node.y())
                    .put("width", node.width())
                    .put("height", node.height())
                    .put("collapsed", node.collapsed()));
        }
        return JsonWriter.compact(JsonValue.object()
                .put("format", 1)
                .put("graph", data.graphId().value())
                .put("panX", data.panX())
                .put("panY", data.panY())
                .put("zoom", data.zoom())
                .put("nodes", nodes));
    }

    public static ShaderGraphEditorData read(String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root.require("format").intValue() != 1) {
            throw new FdxException("Unsupported shader graph editor format");
        }
        JsonValue nodes = root.require("nodes");
        if (!nodes.isArray()) {
            throw new FdxException("Shader graph editor nodes must be an array");
        }
        ShaderGraphEditorNode[] decoded = new ShaderGraphEditorNode[nodes.size()];
        for (int i = 0; i < decoded.length; i++) {
            JsonValue node = nodes.require(i);
            decoded[i] = ShaderGraphEditorNode.of(node.requireString("id"),
                    node.require("x").floatValue(), node.require("y").floatValue(),
                    node.require("width").floatValue(),
                    node.require("height").floatValue(),
                    node.require("collapsed").booleanValue());
        }
        return ShaderGraphEditorData.of(root.requireString("graph"), decoded,
                root.require("panX").floatValue(),
                root.require("panY").floatValue(),
                root.require("zoom").floatValue());
    }
}
