package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import java.util.Locale;
import java.util.regex.Pattern;

/** Display-only socket labels. Connections always retain the original port IDs. */
record ShaderGraphEditorPortLabels(Socket[] inputs, Socket[] outputs) {
    record Socket(ShaderGraphPort port, String name, String type) { }

    private static final Pattern NUMBERED_OUTPUT = Pattern.compile("out[0-9]+");

    static ShaderGraphEditorPortLabels of(ShaderNode node, ShaderGraphEditorDocument document) {
        var names = ShaderGraphEditorInputLabels.forNode(node, document);
        var inputs = node.inputs();
        var outputs = node.outputs();
        var left = new Socket[inputs.length];
        var right = new Socket[outputs.length];
        for (int i = 0; i < inputs.length; i++) {
            left[i] = new Socket(inputs[i], names[i], typeName(inputs[i].type()));
        }
        for (int i = 0; i < outputs.length; i++) {
            String id = outputs[i].id().value();
            String name = id.equals("value") || id.equals("out") || NUMBERED_OUTPUT.matcher(id).matches()
                    ? outputs.length == 1 ? resultName(node.definitionId().value()) : "Output " + (i + 1)
                    : id;
            right[i] = new Socket(outputs[i], name, typeName(outputs[i].type()));
        }
        return new ShaderGraphEditorPortLabels(left, right);
    }

    private static String resultName(String definition) {
        return switch (definition) {
            case StandardShaderNodes.ADD -> "Sum";
            case StandardShaderNodes.SUBTRACT -> "Difference";
            case StandardShaderNodes.MULTIPLY -> "Product";
            case StandardShaderNodes.DIVIDE -> "Quotient";
            case StandardShaderNodes.TIME -> "Seconds";
            case StandardShaderNodes.PANNER -> "UV";
            case StandardShaderNodes.NOISE_2D -> "Noise";
            case StandardShaderNodes.FRESNEL -> "Fresnel";
            case StandardShaderNodes.TEXTURE_SAMPLE -> "Color";
            case StandardShaderNodes.CONSTANT, StandardShaderNodes.PARAMETER -> "Value";
            default -> "Result";
        };
    }

    static String typeName(ShaderGraphType type) {
        if (type.kind() != ShaderGraphTypeKind.VALUE) return type.toString();
        var value = type.valueType();
        var scalar = value.scalarType();
        return switch (value.kind()) {
            case SCALAR -> switch (scalar) {
                case F32 -> "Float";
                case F16 -> "Half";
                case I32 -> "Int";
                case U32 -> "UInt";
                case BOOL -> "Bool";
                default -> scalar.name().toLowerCase(Locale.ROOT);
            };
            case VECTOR -> scalar == ShaderScalarType.F32 ? "Vec" + value.rows() : value.toString();
            case MATRIX -> scalar == ShaderScalarType.F32 ? "Mat" + value.columns() + "×" + value.rows() : value.toString();
            default -> value.toString();
        };
    }

}
