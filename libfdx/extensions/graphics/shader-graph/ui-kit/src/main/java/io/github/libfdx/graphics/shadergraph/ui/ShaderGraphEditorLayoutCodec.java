package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Deterministic codec for the document-wide, editor-only JSON block.
 */
public final class ShaderGraphEditorLayoutCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphEditorLayoutCodec() {
    }

    public static String write(ShaderGraphEditorLayout layout) {
        if (layout == null) {
            throw new FdxException("Shader graph editor layout cannot be null");
        }
        JsonValue graphs = JsonValue.array();
        for (ShaderGraphEditorData graph : layout.graphs()) {
            graphs.add(new JsonReader().parse(ShaderGraphEditorCodec.write(graph)));
        }
        return JsonWriter.compact(JsonValue.object()
                .put("asset", "shader-graph-editor-layout")
                .put("format", CURRENT_VERSION)
                .put("activeGraph", layout.activeGraphId())
                .put("graphs", graphs));
    }

    public static ShaderGraphEditorLayout read(String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root == null || !root.isObject()
                || !"shader-graph-editor-layout".equals(root.requireString("asset"))
                || root.require("format").intValue() != CURRENT_VERSION) {
            throw new FdxException("Unsupported shader graph editor layout format");
        }
        JsonValue graphs = root.require("graphs");
        if (!graphs.isArray()) {
            throw new FdxException("Shader graph editor layout graphs must be an array");
        }
        ShaderGraphEditorData[] values = new ShaderGraphEditorData[graphs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = ShaderGraphEditorCodec.read(JsonWriter.compact(graphs.require(i)));
        }
        return ShaderGraphEditorLayout.of(values, root.requireString("activeGraph"));
    }
}
