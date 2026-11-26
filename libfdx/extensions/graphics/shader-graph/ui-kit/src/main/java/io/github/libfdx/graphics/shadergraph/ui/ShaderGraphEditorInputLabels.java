package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodePropertyKind;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import java.util.List;
import java.util.regex.Pattern;

/** Display names for the ordered operands stored in shader graphs. */
final class ShaderGraphEditorInputLabels {
    private static final Pattern POSITIONAL_ID = Pattern.compile("in[0-9]{6,}");

    private ShaderGraphEditorInputLabels() {
    }

    static String[] forNode(ShaderNode node, ShaderGraphEditorDocument document) {
        ShaderGraphPort[] inputs = node.inputs();
        String[] labels = new String[inputs.length];
        String operation = node.definitionId().value();
        List<String> roles = switch (operation) {
            case StandardShaderNodes.ADD, StandardShaderNodes.MINIMUM, StandardShaderNodes.MAXIMUM,
                    StandardShaderNodes.DOT, StandardShaderNodes.CROSS -> List.of("A", "B");
            case StandardShaderNodes.SUBTRACT -> List.of("Left", "Right");
            case StandardShaderNodes.MULTIPLY -> List.of("Factor A", "Factor B");
            case StandardShaderNodes.DIVIDE -> List.of("Numerator", "Denominator");
            case StandardShaderNodes.FRACTION, StandardShaderNodes.ABSOLUTE, StandardShaderNodes.NEGATE, StandardShaderNodes.CONVERT,
                    StandardShaderNodes.DERIVATIVE_X, StandardShaderNodes.DERIVATIVE_Y -> List.of("Value");
            case StandardShaderNodes.PANNER -> List.of("Speed", "Time", "UV");
            case StandardShaderNodes.COSINE -> List.of("Radians");
            case StandardShaderNodes.NORMALIZE -> List.of("Vector");
            case StandardShaderNodes.MEMBER -> List.of("Structure");
            case StandardShaderNodes.CLAMP -> List.of("Value", "Minimum", "Maximum");
            case StandardShaderNodes.LERP -> List.of("Start", "End", "Weight");
            case StandardShaderNodes.BRANCH -> List.of("Condition", "If true", "If false");
            case StandardShaderNodes.SWITCH -> List.of("Selector", "Default");
            case StandardShaderNodes.LOOP -> List.of("Initial value", "Step");
            case StandardShaderNodes.TEXTURE_SAMPLE -> List.of("Texture", "Sampler", "UV");
            case StandardShaderNodes.DISCARD -> List.of("Condition");
            case StandardShaderNodes.STORAGE_LOAD -> List.of("Resource", "Index", "Dependency");
            case StandardShaderNodes.STORAGE_STORE -> List.of("Resource", "Index", "Value", "Dependency");
            case StandardShaderNodes.ATOMIC_ADD -> List.of("Resource", "Index", "Value");
            case StandardShaderNodes.BARRIER -> List.of("Dependency");
            case StandardShaderNodes.SMOOTHSTEP -> List.of("Lower edge", "Upper edge", "Value");
            case StandardShaderNodes.NOISE_2D -> List.of("UV");
            case StandardShaderNodes.FRESNEL -> List.of("Normal", "Power", "View direction");
            default -> List.of();
        };
        int component = 0;
        for (int i = 0; i < inputs.length; i++) {
            String id = inputs[i].id().value();
            int width = Math.max(1, inputs[i].type().componentCount());
            if (POSITIONAL_ID.matcher(id).matches()) {
                labels[i] = i < roles.size() ? roles.get(i) : variableInput(node, document, i, component, width);
            } else {
                int role = switch (operation) {
                    case StandardShaderNodes.SMOOTHSTEP -> List.of("edge0", "edge1", "value").indexOf(id);
                    case StandardShaderNodes.NOISE_2D -> "uv".equals(id) ? 0 : -1;
                    case StandardShaderNodes.FRESNEL -> List.of("normal", "power", "view").indexOf(id);
                    case StandardShaderNodes.PANNER -> List.of("speed", "time", "uv").indexOf(id);
                    default -> -1;
                };
                labels[i] = role < 0 ? id : roles.get(role);
            }
            component += width;
        }
        return labels;
    }

    private static String variableInput(ShaderNode node, ShaderGraphEditorDocument document,
            int index, int component, int width) {
        switch (node.definitionId().value()) {
            case StandardShaderNodes.CONSTRUCT -> {
                var target = node.property("type");
                if (target != null && target.kind() == ShaderNodePropertyKind.TYPE) {
                    var type = target.typeValue();
                    if (type.kind() == ShaderGraphTypeKind.STRUCT && index < type.structType().fieldCount()) {
                        return type.structType().field(index).id().value();
                    }
                    if (type.kind() == ShaderGraphTypeKind.VALUE && type.valueType().kind() == ShaderValueKind.VECTOR
                            && component + width <= type.componentCount() && component + width <= 4) {
                        return "XYZW".substring(component, component + width);
                    }
                }
            }
            case StandardShaderNodes.SWITCH -> {
                var cases = node.property("cases");
                if (cases != null && cases.kind() == ShaderNodePropertyKind.INTEGER_LIST) {
                    long[] values = cases.integerValues();
                    if (index - 2 < values.length) {
                        return "Case " + values[index - 2];
                    }
                }
            }
            case StandardShaderNodes.FUNCTION_CALL -> {
                var target = node.property("function");
                if (target != null && target.kind() == ShaderNodePropertyKind.STRING) {
                    var function = document.graph(target.stringValue());
                    if (function != null && index < function.parameters().length) {
                        return function.parameters()[index].id().value();
                    }
                }
                return "Argument " + (index + 1);
            }
            case StandardShaderNodes.CUSTOM_FUNCTION -> {
                return "Argument $" + index;
            }
        }
        return "Input " + (index + 1);
    }

}
