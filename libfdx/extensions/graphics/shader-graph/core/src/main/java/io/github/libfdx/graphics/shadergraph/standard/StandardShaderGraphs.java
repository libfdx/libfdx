package io.github.libfdx.graphics.shadergraph.standard;

import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;

/**
 * Provider-neutral standard function graphs used by renderer adapters and
 * application-authored graphs.
 *
 * <p>The library contains only semantic function graphs. It has no dependency
 * on a graphics provider, renderer, editor, or UI system. Applications may
 * call these graphs, copy them as a customization starting point, or replace
 * them completely.</p>
 */
public final class StandardShaderGraphs {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);
    private static final ShaderGraphType MAT3 =
            ShaderGraphType.matrix(ShaderScalarType.F32, 3, 3);
    private static final ShaderGraphType MAT4 =
            ShaderGraphType.matrix(ShaderScalarType.F32, 4, 4);

    public static final ShaderGraph SATURATE = unary("libfdx.math.saturate",
            F32, "clamp($0, 0.0, 1.0)");
    public static final ShaderGraph SAFE_NORMALIZE = unary(
            "libfdx.math.safe-normalize", VEC3,
            "select($0 * inverseSqrt(max(dot($0, $0), 0.00000001)), "
                    + "vec3<f32>(0.0, 0.0, 1.0), "
                    + "dot($0, $0) <= 0.00000001)");
    public static final ShaderGraph REMAP = function(
            "libfdx.math.remap", F32,
            new Input[] {
                    input("value", F32), input("input_min", F32),
                    input("input_max", F32), input("output_min", F32),
                    input("output_max", F32)
            },
            "mix($3, $4, clamp(($0 - $1) / max($2 - $1, "
                    + "0.00000001), 0.0, 1.0))");

    public static final ShaderGraph TRANSFORM_POINT = function(
            "libfdx.coordinate.transform-point", VEC3,
            new Input[] {
                    input("transform", MAT4), input("point", VEC3)
            }, "($0 * vec4<f32>($1, 1.0)).xyz");
    public static final ShaderGraph TRANSFORM_DIRECTION = function(
            "libfdx.coordinate.transform-direction", VEC3,
            new Input[] {
                    input("transform", MAT3), input("direction", VEC3)
            }, "normalize($0 * $1)");

    public static final ShaderGraph UNPACK_NORMAL = unary(
            "libfdx.normal.unpack-rgb", VEC4,
            "normalize($0.xyz * vec3<f32>(2.0) - vec3<f32>(1.0))",
            VEC3);
    public static final ShaderGraph NORMAL_FROM_DERIVATIVES = function(
            "libfdx.normal.from-derivatives", VEC3,
            new Input[] {
                    input("position_dx", VEC3),
                    input("position_dy", VEC3)
            }, "normalize(cross($0, $1))");
    public static final ShaderGraph UV_TRANSFORM = function(
            "libfdx.texture.uv-transform", VEC2,
            new Input[] {
                    input("uv", VEC2), input("scale", VEC2),
                    input("offset", VEC2)
            }, "$0 * $1 + $2");

    public static final ShaderGraph SRGB_TO_LINEAR = unary(
            "libfdx.color.srgb-to-linear", VEC3,
            "select(pow(($0 + vec3<f32>(0.055)) / "
                    + "vec3<f32>(1.055), vec3<f32>(2.4)), "
                    + "$0 / vec3<f32>(12.92), "
                    + "$0 <= vec3<f32>(0.04045))");
    public static final ShaderGraph LINEAR_TO_SRGB = unary(
            "libfdx.color.linear-to-srgb", VEC3,
            "select(vec3<f32>(1.055) * pow(max($0, vec3<f32>(0.0)), "
                    + "vec3<f32>(0.41666667)) - vec3<f32>(0.055), "
                    + "$0 * vec3<f32>(12.92), "
                    + "$0 <= vec3<f32>(0.0031308))");

    public static final ShaderGraph LAMBERT = function(
            "libfdx.lighting.lambert", F32,
            new Input[] {
                    input("normal", VEC3), input("light_direction", VEC3)
            }, "max(dot(normalize($0), normalize($1)), 0.0)");
    public static final ShaderGraph FRESNEL_SCHLICK = function(
            "libfdx.brdf.fresnel-schlick", VEC3,
            new Input[] {
                    input("f0", VEC3), input("cos_theta", F32)
            }, "$0 + (vec3<f32>(1.0) - $0) * "
                    + "pow(clamp(1.0 - $1, 0.0, 1.0), 5.0)");
    public static final ShaderGraph GGX_DISTRIBUTION = function(
            "libfdx.brdf.ggx-distribution", F32,
            new Input[] {
                    input("normal_dot_half", F32),
                    input("roughness", F32)
            }, "max($1 * $1 * $1 * $1, 0.0001) / "
                    + "max(3.14159265 * pow($0 * $0 * "
                    + "(max($1 * $1 * $1 * $1, 0.0001) - 1.0) "
                    + "+ 1.0, 2.0), 0.0001)");
    public static final ShaderGraph GGX_GEOMETRY = function(
            "libfdx.brdf.ggx-geometry", F32,
            new Input[] {
                    input("normal_dot_view", F32),
                    input("normal_dot_light", F32),
                    input("roughness", F32)
            }, "($0 / max($0 * (1.0 - pow(($2 + 1.0) * "
                    + "($2 + 1.0) / 8.0, 1.0)) + "
                    + "pow(($2 + 1.0) * ($2 + 1.0) / 8.0, 1.0), "
                    + "0.0001)) * ($1 / max($1 * (1.0 - "
                    + "pow(($2 + 1.0) * ($2 + 1.0) / 8.0, 1.0)) "
                    + "+ pow(($2 + 1.0) * ($2 + 1.0) / 8.0, 1.0), "
                    + "0.0001))");

    public static final ShaderGraph UNPACK_RG_DEPTH = unary(
            "libfdx.shadow.unpack-rg-depth", VEC2,
            "dot($0, vec2<f32>(1.0, 0.0039215686))", F32);
    public static final ShaderGraph SHADOW_VISIBILITY = function(
            "libfdx.shadow.visibility", F32,
            new Input[] {
                    input("receiver_depth", F32),
                    input("stored_depth", F32),
                    input("bias", F32)
            }, "select(0.0, 1.0, $0 - $2 <= $1)");

    public static final ShaderGraph LINEAR_FOG = function(
            "libfdx.fog.linear-factor", F32,
            new Input[] {
                    input("distance", F32), input("start", F32),
                    input("end", F32)
            }, "clamp(($2 - $0) / max($2 - $1, 0.0001), 0.0, 1.0)");
    public static final ShaderGraph EXPONENTIAL_FOG = function(
            "libfdx.fog.exponential-factor", F32,
            new Input[] {
                    input("distance", F32), input("density", F32)
            }, "exp(-max($0, 0.0) * max($1, 0.0))");

    public static final ShaderGraph REINHARD_TONE_MAP = unary(
            "libfdx.post.reinhard-tone-map", VEC3,
            "$0 / (vec3<f32>(1.0) + max($0, vec3<f32>(0.0)))");
    public static final ShaderGraph ACES_TONE_MAP = unary(
            "libfdx.post.aces-tone-map", VEC3,
            "clamp(($0 * (vec3<f32>(2.51) * $0 + "
                    + "vec3<f32>(0.03))) / ($0 * "
                    + "(vec3<f32>(2.43) * $0 + vec3<f32>(0.59)) "
                    + "+ vec3<f32>(0.14)), vec3<f32>(0.0), "
                    + "vec3<f32>(1.0))");

    private static final ShaderGraph[] ALL = {
            SATURATE, SAFE_NORMALIZE, REMAP,
            TRANSFORM_POINT, TRANSFORM_DIRECTION,
            UV_TRANSFORM, UNPACK_NORMAL, NORMAL_FROM_DERIVATIVES,
            SRGB_TO_LINEAR, LINEAR_TO_SRGB,
            LAMBERT, FRESNEL_SCHLICK, GGX_DISTRIBUTION, GGX_GEOMETRY,
            UNPACK_RG_DEPTH, SHADOW_VISIBILITY,
            LINEAR_FOG, EXPONENTIAL_FOG,
            REINHARD_TONE_MAP, ACES_TONE_MAP
    };
    private static final ShaderGraphLibrary LIBRARY =
            ShaderGraphLibrary.of(ALL);

    private StandardShaderGraphs() {
    }

    /**
     * Returns every standard function graph.
     *
     * @return defensive graph-array copy
     */
    public static ShaderGraph[] all() {
        return ALL.clone();
    }

    /**
     * Returns the immutable standard dependency library.
     *
     * @return standard library
     */
    public static ShaderGraphLibrary library() {
        return LIBRARY;
    }

    private static ShaderGraph unary(String id, ShaderGraphType input,
            String expression) {
        return unary(id, input, expression, input);
    }

    private static ShaderGraph unary(String id, ShaderGraphType input,
            String expression, ShaderGraphType output) {
        return function(id, output,
                new Input[] { input("value", input) }, expression);
    }

    private static ShaderGraph function(String id, ShaderGraphType output,
            Input[] inputs, String expression) {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                id, ShaderGraphKind.FUNCTION);
        ShaderExpression[] values = new ShaderExpression[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            Input input = inputs[i];
            graph.parameter(ShaderGraphParameter.of(input.id,
                    input.type, ShaderGraphParameterKind.FUNCTION_INPUT,
                    ShaderGraphLiteral.zero(input.type)));
            values[i] = graph.parameter("input_" + i, input.id);
        }
        ShaderExpression result = graph.customWgsl("result", output,
                expression, values);
        graph.output("value", "", result);
        return graph.build();
    }

    private static Input input(String id, ShaderGraphType type) {
        return new Input(id, type);
    }

    private record Input(String id, ShaderGraphType type) {
    }
}
