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
 * Framework-owned standard PBR final linear-lighting extension contract.
 *
 * <p>A replacement graph receives the renderer's complete light accumulation
 * and the principal surface/view values. It returns a linear RGB
 * {@code color}; fog, exposure, tone mapping, and display conversion run
 * afterwards.</p>
 */
public final class StandardPbrLightingGraph {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);

    private StandardPbrLightingGraph() {
    }

    /**
     * Creates the identity final-lighting extension.
     *
     * @return immutable function graph
     */
    public static ShaderGraph create() {
        ShaderGraphBuilder graph = builder(
                "libfdx.standard.pbr.lighting");
        ShaderExpression color = graph.parameter(
                "lit_color_input", "lit_color");
        consumeRemainingInputs(graph);
        graph.output("color", "color", color);
        return graph.build();
    }

    /**
     * Creates an unlit lighting extension that returns base color plus
     * emissive.
     *
     * @return immutable function graph
     */
    public static ShaderGraph unlit() {
        ShaderGraphBuilder graph = builder(
                "libfdx.standard.unlit.lighting");
        graph.parameter("lit_color_input", "lit_color");
        ShaderExpression base = graph.parameter(
                "base_color_input", "base_color");
        graph.parameter("normal_input", "normal");
        graph.parameter("view_direction_input", "view_direction");
        graph.parameter("world_position_input", "world_position");
        ShaderExpression emissive = graph.parameter(
                "emissive_input", "emissive");
        graph.parameter("metallic_input", "metallic");
        graph.parameter("roughness_input", "roughness");
        graph.parameter("occlusion_input", "occlusion");
        graph.parameter("uv_input", "uv");
        graph.output("color", "color",
                graph.add("unlit_color", base, emissive));
        return graph.build();
    }

    private static ShaderGraphBuilder builder(String id) {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(id,
                ShaderGraphKind.FUNCTION);
        input(graph, "lit_color", VEC3, "litColor");
        input(graph, "base_color", VEC3, "baseColor");
        input(graph, "normal", VEC3, "normal");
        input(graph, "view_direction", VEC3, "viewDirection");
        input(graph, "world_position", VEC3, "worldPosition");
        input(graph, "emissive", VEC3, "emissive");
        input(graph, "metallic", F32, "metallic");
        input(graph, "roughness", F32, "roughness");
        input(graph, "occlusion", F32, "occlusion");
        input(graph, "uv", VEC2, "uv0");
        return graph;
    }

    private static void consumeRemainingInputs(
            ShaderGraphBuilder graph) {
        graph.parameter("base_color_input", "base_color");
        graph.parameter("normal_input", "normal");
        graph.parameter("view_direction_input", "view_direction");
        graph.parameter("world_position_input", "world_position");
        graph.parameter("emissive_input", "emissive");
        graph.parameter("metallic_input", "metallic");
        graph.parameter("roughness_input", "roughness");
        graph.parameter("occlusion_input", "occlusion");
        graph.parameter("uv_input", "uv");
    }

    private static void input(ShaderGraphBuilder graph, String id,
            ShaderGraphType type, String semantic) {
        graph.parameter(ShaderGraphParameter.semantic(id, type,
                ShaderGraphParameterKind.FUNCTION_INPUT,
                ShaderGraphLiteral.zero(type), semantic));
    }
}
