package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/**
 * Deterministic semantic codec for complete compute-program assets.
 */
public final class ShaderGraphComputeProgramCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphComputeProgramCodec() {
    }

    public static String write(ShaderGraphComputeProgram program) {
        if (program == null) {
            throw new FdxException(
                    "Shader graph compute program cannot be null");
        }
        return JsonWriter.compact(JsonValue.object()
                .put("asset", "compute-program")
                .put("format", CURRENT_VERSION)
                .put("id", program.id().value())
                .put("graph",
                        ShaderGraphProgramCodec.graph(
                                program.graph()))
                .put("entryPoint", program.entryPoint())
                .put("workgroupX", program.workgroupX())
                .put("workgroupY", program.workgroupY())
                .put("workgroupZ", program.workgroupZ()));
    }

    public static ShaderGraphComputeProgram read(String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root == null || !root.isObject()
                || !"compute-program".equals(
                        root.requireString("asset"))
                || root.require("format").intValue()
                        != CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph compute-program asset format");
        }
        return ShaderGraphComputeProgram.builder(
                        root.requireString("id"),
                        ShaderGraphProgramCodec.readGraph(
                                root.require("graph")))
                .entryPoint(root.requireString("entryPoint"))
                .workgroupSize(
                        root.require("workgroupX").intValue(),
                        root.require("workgroupY").intValue(),
                        root.require("workgroupZ").intValue())
                .build();
    }
}
