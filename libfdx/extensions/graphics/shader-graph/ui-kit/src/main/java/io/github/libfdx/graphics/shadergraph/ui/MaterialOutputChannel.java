package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;

/** Ordered PBR input sockets and their existing surface output semantics. */
enum MaterialOutputChannel {
    BASE_COLOR("Base Color", "baseColor", "base_color", 3),
    ALPHA("Alpha", "alpha", "alpha", 1),
    EMISSIVE("Emissive", "emissive", "emissive", 3),
    NORMAL("Normal", "normal", "normal", 3),
    METALLIC("Metallic", "metallic", "metallic", 1),
    ROUGHNESS("Roughness", "roughness", "roughness", 1),
    OCCLUSION("Occlusion", "occlusion", "occlusion", 1);

    final String label;
    final String semantic;
    final String id;
    final String typeLabel;
    final ShaderGraphType type;

    MaterialOutputChannel(String label, String semantic, String id, int components) {
        this.label = label;
        this.semantic = semantic;
        this.id = id;
        type = components == 1 ? ShaderGraphType.scalar(ShaderScalarType.F32)
                : ShaderGraphType.vector(ShaderScalarType.F32, components);
        typeLabel = ShaderGraphEditorPortLabels.typeName(type);
    }

    ShaderGraphOutput binding(ShaderGraphOutput[] outputs) {
        for (var output : outputs) {
            if (semantic.equalsIgnoreCase(output.semantic()) || id.equalsIgnoreCase(output.id().value())) return output;
        }
        return null;
    }

}
