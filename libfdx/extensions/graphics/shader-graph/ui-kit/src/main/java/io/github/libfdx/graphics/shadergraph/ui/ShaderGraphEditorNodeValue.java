package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodePropertyKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import java.util.StringJoiner;

/** Display and authoring information for a constant or a reference to a graph input. */
record ShaderGraphEditorNodeValue(ShaderNode node, ShaderGraphParameter parameter, ShaderGraphLiteral literal,
        String source, String readout) {
    static ShaderGraphEditorNodeValue of(ShaderNode node, ShaderGraph graph) {
        if (node.definitionId().value().equals(StandardShaderNodes.CONSTANT)) {
            var property = node.property("literal");
            if (property == null || property.kind() != ShaderNodePropertyKind.LITERAL) return null;
            var literal = property.literalValue();
            return new ShaderGraphEditorNodeValue(node, null, literal, "Constant", format(literal));
        }
        if (!node.definitionId().value().equals(StandardShaderNodes.PARAMETER)) return null;
        var property = node.property("parameter");
        if (property == null || property.kind() != ShaderNodePropertyKind.STRING) return null;
        String id = property.stringValue();
        for (var parameter : graph.parameters()) {
            if (!parameter.id().value().equals(id)) continue;
            String source = switch (parameter.kind()) {
                case MATERIAL -> "Material parameter";
                case STAGE_INPUT -> "Stage input";
                case FUNCTION_INPUT -> "Function input";
                case STATIC_SWITCH -> "Static switch";
            };
            var literal = parameter.defaultValue();
            if (literal == null && editableComponents(parameter.type()) > 0) literal = ShaderGraphLiteral.zero(parameter.type());
            boolean supplied = parameter.kind() == ShaderGraphParameterKind.STAGE_INPUT
                    || parameter.kind() == ShaderGraphParameterKind.FUNCTION_INPUT;
            String value = supplied || literal == null ? id : id + " = " + format(literal);
            return new ShaderGraphEditorNodeValue(node, parameter, literal, source, value);
        }
        return new ShaderGraphEditorNodeValue(node, null, null, "Unknown parameter", id);
    }

    boolean externallySupplied() {
        return parameter != null && (parameter.kind() == ShaderGraphParameterKind.STAGE_INPUT
                || parameter.kind() == ShaderGraphParameterKind.FUNCTION_INPUT);
    }

    boolean editableInline() {
        return literal != null && !externallySupplied() && editableComponents(literal.type()) > 0;
    }

    void apply(ShaderGraphEditorSession session, ShaderGraphLiteral value) {
        if (parameter == null) {
            session.setNodeProperty(node.id().value(), ShaderNodeProperty.literal("literal", value));
        } else if (externallySupplied()) {
            session.editActiveGraph("Replace input with constant", graph -> ShaderGraphSemanticEdits.useConstant(graph, node, value));
        } else {
            session.editActiveGraph("Edit parameter default",
                    graph -> ShaderGraphSemanticEdits.parameterDefault(graph, parameter.id(), value));
        }
    }

    static int editableComponents(ShaderGraphType type) {
        if (type.kind() != ShaderGraphTypeKind.VALUE) return 0;
        var value = type.valueType();
        if (value.kind() == ShaderValueKind.VECTOR && value.scalarType() == ShaderScalarType.F32) return value.rows();
        if (value.kind() != ShaderValueKind.SCALAR) return 0;
        return switch (value.scalarType()) {
            case F32, I32, U32, BOOL -> 1;
            default -> 0;
        };
    }

    static String componentText(ShaderGraphLiteral literal) {
        return switch (literal.type().valueType().scalarType()) {
            case F32 -> Float.toString(Float.intBitsToFloat((int) literal.bits()));
            case I32 -> Integer.toString((int) literal.bits());
            case U32 -> Integer.toUnsignedString((int) literal.bits());
            case BOOL -> Boolean.toString(literal.bits() != 0);
            default -> throw new IllegalArgumentException("Unsupported scalar type");
        };
    }

    static String format(ShaderGraphLiteral literal) {
        int count = editableComponents(literal.type());
        if (count == 0) return ShaderGraphEditorPortLabels.typeName(literal.type());
        if (count == 1) return componentText(literal);
        var components = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < count; i++) components.add(componentText(literal.element(i)));
        return components.toString();
    }

}
