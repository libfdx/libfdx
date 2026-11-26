package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.json.JsonValue;

/** Optional editor-only range for previewing a scalar float material parameter on a surface. */
public record ShaderGraphPreviewAnimation(String parameter, float min, float max) {
    private static final ShaderGraphType FLOAT = ShaderGraphType.scalar(ShaderScalarType.F32);

    public ShaderGraphPreviewAnimation {
        parameter = ShaderGraphId.of(parameter).value();
        if (!Float.isFinite(min) || !Float.isFinite(max) || min >= max) {
            throw new IllegalArgumentException("Minimum must be finite and less than maximum");
        }
    }

    public static boolean supports(ShaderGraphParameter input) {
        return input != null && input.kind() == ShaderGraphParameterKind.MATERIAL && FLOAT.equals(input.type());
    }

    public boolean appliesTo(ShaderGraph graph) {
        return graph != null && graph.kind() == ShaderGraphKind.SURFACE && supports(graph.parameter(ShaderGraphId.of(parameter)));
    }

    /** Writes the preset into the supplied editor object, leaving its canvas metadata intact. */
    public JsonValue writeTo(JsonValue editor) {
        return editor.put("previewAnimation", JsonValue.object().put("parameter", parameter).put("min", min).put("max", max));
    }

    /** Returns null when the editor object has no animation preset. */
    public static ShaderGraphPreviewAnimation readFrom(JsonValue editor) {
        JsonValue value = editor.get("previewAnimation");
        return value == null || value.isNull() ? null : new ShaderGraphPreviewAnimation(value.requireString("parameter"),
                value.require("min").floatValue(), value.require("max").floatValue());
    }

}
