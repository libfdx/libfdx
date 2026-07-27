package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Deterministic semantic codec for complete vertex/fragment program assets.
 */
public final class ShaderGraphProgramCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphProgramCodec() {
    }

    public static String write(ShaderGraphProgram program) {
        if (program == null) {
            throw new FdxException(
                    "Shader graph program cannot be null");
        }
        JsonValue root = JsonValue.object()
                .put("asset", "program")
                .put("format", CURRENT_VERSION)
                .put("id", program.id().value())
                .put("vertex", graph(program.vertex()))
                .put("fragment", graph(program.fragment()))
                .put("vertexEntryPoint",
                        program.vertexEntryPoint())
                .put("fragmentEntryPoint",
                        program.fragmentEntryPoint())
                .put("materialGroup",
                        program.materialGroup())
                .put("materialBinding",
                        program.materialBinding());
        return JsonWriter.compact(root);
    }

    public static ShaderGraphProgram read(String source) {
        JsonValue root = new JsonReader().parse(source);
        require(root, "program");
        return ShaderGraphProgram.builder(
                        root.requireString("id"),
                        readGraph(root.require("vertex")),
                        readGraph(root.require("fragment")))
                .entryPoints(
                        root.requireString("vertexEntryPoint"),
                        root.requireString("fragmentEntryPoint"))
                .materialBinding(
                        root.require("materialGroup").intValue(),
                        root.require("materialBinding").intValue())
                .build();
    }

    static JsonValue graph(ShaderGraph graph) {
        return new JsonReader().parse(
                ShaderGraphCodec.write(graph));
    }

    static ShaderGraph readGraph(JsonValue value) {
        return ShaderGraphCodec.read(
                JsonWriter.compact(value));
    }

    static void require(JsonValue root, String asset) {
        if (root == null || !root.isObject()
                || !asset.equals(root.requireString("asset"))
                || root.require("format").intValue()
                        != CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph " + asset
                            + " asset format");
        }
    }
}
