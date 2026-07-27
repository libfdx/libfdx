package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;

/**
 * Framework-owned customizable PBR surface-evaluation graph.
 */
public final class StandardPbrSurfaceGraph {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private StandardPbrSurfaceGraph() {
    }

    /**
     * Creates a new immutable standard PBR surface graph.
     *
     * @return surface graph
     */
    public static ShaderGraph create() {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "libfdx.standard.pbr.surface", ShaderGraphKind.SURFACE);
        input(graph, "base_color", VEC3, "baseColor");
        input(graph, "alpha", F32, "alpha");
        input(graph, "normal", VEC3, "normal");
        input(graph, "metallic", F32, "metallic");
        input(graph, "roughness", F32, "roughness");
        input(graph, "occlusion", F32, "occlusion");
        input(graph, "emissive", VEC3, "emissive");
        input(graph, "uv", VEC2, "uv0");
        graph.parameter(ShaderGraphParameter.of("tint", VEC4,
                ShaderGraphParameterKind.MATERIAL,
                vector(VEC4, 1, 1, 1, 1)));
        graph.parameter(ShaderGraphParameter.of("emissive_gain", F32,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.f32(1)));

        ShaderExpression base = graph.parameter("base_input", "base_color");
        ShaderExpression alpha = graph.parameter("alpha_input", "alpha");
        ShaderExpression normal = graph.parameter("normal_input", "normal");
        ShaderExpression metallic = graph.parameter("metallic_input", "metallic");
        ShaderExpression roughness = graph.parameter("roughness_input", "roughness");
        ShaderExpression occlusion = graph.parameter("occlusion_input", "occlusion");
        ShaderExpression emissive = graph.parameter("emissive_input", "emissive");
        graph.parameter("uv_input", "uv");
        ShaderExpression tint = graph.parameter("tint_input", "tint");
        ShaderExpression tintRgb = graph.member("tint_rgb", tint, "xyz", VEC3);
        ShaderExpression tintAlpha = graph.member("tint_alpha", tint, "w", F32);
        ShaderExpression gain = graph.parameter("emissive_gain_input",
                "emissive_gain");
        ShaderExpression gain3 = graph.construct("emissive_gain3", VEC3,
                gain, gain, gain);

        graph.output("base_color", "baseColor",
                graph.multiply("tinted_base", base, tintRgb));
        graph.output("alpha", "alpha",
                graph.multiply("tinted_alpha", alpha, tintAlpha));
        graph.output("normal", "normal", normal);
        graph.output("metallic", "metallic", metallic);
        graph.output("roughness", "roughness", roughness);
        graph.output("occlusion", "occlusion", occlusion);
        graph.output("emissive", "emissive",
                graph.multiply("scaled_emissive", emissive, gain3));
        return graph.build();
    }

    private static void input(ShaderGraphBuilder graph, String id,
            ShaderGraphType type, String semantic) {
        graph.parameter(ShaderGraphParameter.semantic(id, type,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.zero(type), semantic));
    }

    private static ShaderGraphLiteral vector(ShaderGraphType type,
            float x, float y, float z, float w) {
        return ShaderGraphLiteral.composite(type,
                ShaderGraphLiteral.f32(x), ShaderGraphLiteral.f32(y),
                ShaderGraphLiteral.f32(z), ShaderGraphLiteral.f32(w));
    }
}
