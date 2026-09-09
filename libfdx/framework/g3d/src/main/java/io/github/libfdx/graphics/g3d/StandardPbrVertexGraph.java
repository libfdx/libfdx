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
 * Framework-owned PBR local-space vertex transformation extension contract.
 *
 * <p>A replacement graph receives the post-skinning local position, local
 * normal, tangent XYZW, and UV0/UV1. It must return {@code position} and {@code normal}
 * values. The optional {@code tangent} output replaces the tangent; omit it to retain
 * the input. Deformations that change tangent directions should supply that output.
 * Missing tangent/UV1 attributes arrive as zeros. Model/view projection and
 * renderer-owned skinning remain outside this function graph.</p>
 */
public final class StandardPbrVertexGraph {
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private StandardPbrVertexGraph() {
    }

    /**
     * Creates the identity vertex extension.
     *
     * @return immutable function graph
     */
    public static ShaderGraph create() {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "libfdx.standard.pbr.vertex", ShaderGraphKind.FUNCTION);
        input(graph, "local_position", VEC3, "localPosition");
        input(graph, "local_normal", VEC3, "localNormal");
        input(graph, "uv", VEC2, "uv0");
        input(graph, "uv1", VEC2, "uv1");
        input(graph, "local_tangent", VEC4, "localTangent");
        ShaderExpression position = graph.parameter(
                "local_position_input", "local_position");
        ShaderExpression normal = graph.parameter(
                "local_normal_input", "local_normal");
        graph.parameter("uv_input", "uv");
        graph.parameter("uv1_input", "uv1");
        ShaderExpression tangent = graph.parameter("local_tangent_input", "local_tangent");
        graph.output("position", "position", position);
        graph.output("normal", "normal", normal);
        graph.output("tangent", "tangent", tangent);
        return graph.build();
    }

    private static void input(ShaderGraphBuilder graph, String id,
            ShaderGraphType type, String semantic) {
        graph.parameter(ShaderGraphParameter.semantic(id, type,
                ShaderGraphParameterKind.FUNCTION_INPUT,
                ShaderGraphLiteral.zero(type), semantic));
    }
}
