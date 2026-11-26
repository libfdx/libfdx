package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Immutable extensible node palette. The standard palette intentionally uses
 * typed semantic node factories and contains no generated shader snippets.
 */
public final class ShaderGraphEditorPalette {
    private static final ShaderGraphKind[] EXPRESSION_KINDS = {
            ShaderGraphKind.FUNCTION, ShaderGraphKind.SUBGRAPH,
            ShaderGraphKind.SURFACE, ShaderGraphKind.VERTEX,
            ShaderGraphKind.FRAGMENT, ShaderGraphKind.COMPUTE
    };
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphEditorPalette STANDARD =
            new ShaderGraphEditorPalette(standardTemplates());
    private final ShaderGraphEditorNodeTemplate[] templates;

    public ShaderGraphEditorPalette(
            ShaderGraphEditorNodeTemplate... templates) {
        if (templates == null) {
            throw new FdxException(
                    "Shader graph editor palette cannot be null");
        }
        this.templates = templates.clone();
        Arrays.sort(this.templates);
        for (int i = 0; i < this.templates.length; i++) {
            if (this.templates[i] == null || i > 0
                    && this.templates[i - 1]
                            .compareTo(this.templates[i]) == 0) {
                throw new FdxException(
                        "Shader graph editor palette entries must be unique");
            }
        }
    }

    public static ShaderGraphEditorPalette standard() {
        return STANDARD;
    }

    public ShaderGraphEditorNodeTemplate[] templates() {
        return templates.clone();
    }

    public ShaderGraphEditorNodeTemplate[] templates(ShaderGraphKind kind,
            String query) {
        String filter = query != null
                ? query.trim().toLowerCase(Locale.ROOT) : "";
        List<ShaderGraphEditorNodeTemplate> result = new ArrayList<>();
        for (ShaderGraphEditorNodeTemplate template : templates) {
            if (template.supports(kind)
                    && (filter.isEmpty()
                            || template.label().toLowerCase(Locale.ROOT)
                                    .contains(filter)
                            || template.category().toLowerCase(Locale.ROOT)
                                    .contains(filter)
                            || template.definitionId().value()
                                    .contains(filter))) {
                result.add(template);
            }
        }
        return result.toArray(ShaderGraphEditorNodeTemplate[]::new);
    }

    List<ShaderGraphEditorNodeTemplate> forGraph(ShaderGraph graph) {
        var entries = new ArrayList<>(Arrays.asList(templates(graph.kind(), "")));
        for (var param : graph.parameters()) entries.add(parameter(param));
        for (var resource : graph.resources()) entries.add(resource(resource));
        entries.sort(null);
        return entries;
    }

    public static ShaderGraphEditorNodeTemplate parameter(
            ShaderGraphParameter parameter) {
        if (parameter == null) {
            throw new FdxException(
                    "Shader graph parameter palette entry cannot be null");
        }
        return new ShaderGraphEditorNodeTemplate("Inputs",
                "Parameter: " + parameter.id(),
                StandardShaderNodes.PARAMETER, 1,
                nodeId -> ShaderNode.of(nodeId,
                        StandardShaderNodes.PARAMETER, 1,
                        new ShaderGraphPort[0],
                        new ShaderGraphPort[] {
                                ShaderGraphPort.required("value",
                                        parameter.type())
                        },
                        ShaderNodeProperty.string("parameter",
                                parameter.id().value())),
                EXPRESSION_KINDS);
    }

    public static ShaderGraphEditorNodeTemplate resource(
            ShaderGraphResource resource) {
        if (resource == null) {
            throw new FdxException(
                    "Shader graph resource palette entry cannot be null");
        }
        return new ShaderGraphEditorNodeTemplate("Resources",
                "Resource: " + resource.id(),
                StandardShaderNodes.RESOURCE, 1,
                nodeId -> ShaderNode.of(nodeId,
                        StandardShaderNodes.RESOURCE, 1,
                        new ShaderGraphPort[0],
                        new ShaderGraphPort[] {
                                ShaderGraphPort.required("value",
                                        resource.type())
                        },
                        ShaderNodeProperty.string("resource",
                                resource.id().value())),
                EXPRESSION_KINDS);
    }

    private static ShaderGraphEditorNodeTemplate[] standardTemplates() {
        List<ShaderGraphEditorNodeTemplate> result = new ArrayList<>();
        result.add(new ShaderGraphEditorNodeTemplate("Values", "Float",
                StandardShaderNodes.CONSTANT, 1,
                nodeId -> ShaderNode.of(nodeId,
                        StandardShaderNodes.CONSTANT, 1,
                        new ShaderGraphPort[0],
                        output(F32),
                        ShaderNodeProperty.literal("literal",
                                ShaderGraphLiteral.f32(0.0f))),
                EXPRESSION_KINDS));
        result.add(binary("Math", "Add", StandardShaderNodes.ADD));
        result.add(binary("Math", "Subtract",
                StandardShaderNodes.SUBTRACT));
        result.add(binary("Math", "Multiply",
                StandardShaderNodes.MULTIPLY));
        result.add(binary("Math", "Divide", StandardShaderNodes.DIVIDE));
        result.add(binary("Math", "Minimum",
                StandardShaderNodes.MINIMUM));
        result.add(binary("Math", "Maximum",
                StandardShaderNodes.MAXIMUM));
        result.add(new ShaderGraphEditorNodeTemplate("Inputs", "Time", StandardShaderNodes.TIME, 1,
                id -> ShaderNode.of(id, StandardShaderNodes.TIME, 1, new ShaderGraphPort[0], output(F32)), ShaderGraphKind.SURFACE));
        var vec2 = ShaderGraphType.vector(ShaderScalarType.F32, 2);
        result.add(new ShaderGraphEditorNodeTemplate("Values", "Vector2", StandardShaderNodes.CONSTANT, 1,
                id -> ShaderNode.of(id, StandardShaderNodes.CONSTANT, 1, new ShaderGraphPort[0], output(vec2),
                        ShaderNodeProperty.literal("literal", ShaderGraphLiteral.zero(vec2))), EXPRESSION_KINDS));
        result.add(new ShaderGraphEditorNodeTemplate("UV", "Panner", StandardShaderNodes.PANNER, 1,
                id -> ShaderNode.of(id, StandardShaderNodes.PANNER, 1, new ShaderGraphPort[] {
                        ShaderGraphPort.required("speed", vec2), ShaderGraphPort.required("time", F32),
                        ShaderGraphPort.required("uv", vec2) }, output(vec2)), EXPRESSION_KINDS));
        result.add(unary("Math", "Fraction", StandardShaderNodes.FRACTION));
        result.add(unary("Math", "Cosine", StandardShaderNodes.COSINE));
        result.add(unary("Math", "Negate", StandardShaderNodes.NEGATE));
        result.add(unary("Math", "Absolute",
                StandardShaderNodes.ABSOLUTE));
        result.add(effect("Math", "Smoothstep", StandardShaderNodes.SMOOTHSTEP,
                ShaderGraphPort.required("edge0", F32), ShaderGraphPort.required("edge1", F32),
                ShaderGraphPort.required("value", F32)));
        result.add(effect("Procedural", "Noise 2D", StandardShaderNodes.NOISE_2D,
                ShaderGraphPort.required("uv", ShaderGraphType.vector(ShaderScalarType.F32, 2))));
        ShaderGraphType direction = ShaderGraphType.vector(ShaderScalarType.F32, 3);
        result.add(effect("Surface", "Fresnel", StandardShaderNodes.FRESNEL,
                ShaderGraphPort.required("normal", direction), ShaderGraphPort.required("power", F32),
                ShaderGraphPort.required("view", direction)));
        result.add(new ShaderGraphEditorNodeTemplate("Math", "Lerp",
                StandardShaderNodes.LERP, 1,
                nodeId -> ShaderNode.of(nodeId, StandardShaderNodes.LERP, 1,
                        new ShaderGraphPort[] {
                                ShaderGraphPort.required("in000000", F32),
                                ShaderGraphPort.required("in000001", F32),
                                ShaderGraphPort.required("in000002", F32)
                        }, output(F32)),
                EXPRESSION_KINDS));
        result.add(new ShaderGraphEditorNodeTemplate("Functions",
                "Custom WGSL Function",
                StandardShaderNodes.CUSTOM_FUNCTION, 1,
                nodeId -> ShaderNode.of(nodeId,
                        StandardShaderNodes.CUSTOM_FUNCTION, 1,
                        new ShaderGraphPort[0], output(F32),
                        ShaderNodeProperty.string("body",
                                "return 0.0;")),
                ShaderGraphKind.FUNCTION, ShaderGraphKind.VERTEX,
                ShaderGraphKind.FRAGMENT, ShaderGraphKind.COMPUTE));
        return result.toArray(ShaderGraphEditorNodeTemplate[]::new);
    }

    private static ShaderGraphEditorNodeTemplate effect(String category, String label, String definition,
            ShaderGraphPort... inputs) {
        return new ShaderGraphEditorNodeTemplate(category, label, definition, 1,
                nodeId -> ShaderNode.of(nodeId, definition, 1, inputs, output(F32)), EXPRESSION_KINDS);
    }

    private static ShaderGraphEditorNodeTemplate binary(String category,
            String label, String definition) {
        return new ShaderGraphEditorNodeTemplate(category, label, definition,
                1, nodeId -> ShaderNode.of(nodeId, definition, 1,
                        new ShaderGraphPort[] {
                                ShaderGraphPort.required("in000000", F32),
                                ShaderGraphPort.required("in000001", F32)
                        }, output(F32)),
                EXPRESSION_KINDS);
    }

    private static ShaderGraphEditorNodeTemplate unary(String category,
            String label, String definition) {
        return new ShaderGraphEditorNodeTemplate(category, label, definition,
                1, nodeId -> ShaderNode.of(nodeId, definition, 1,
                        new ShaderGraphPort[] {
                                ShaderGraphPort.required("in000000", F32)
                        }, output(F32)),
                EXPRESSION_KINDS);
    }

    private static ShaderGraphPort[] output(ShaderGraphType type) {
        return new ShaderGraphPort[] {
                ShaderGraphPort.required("value", type)
        };
    }
}
