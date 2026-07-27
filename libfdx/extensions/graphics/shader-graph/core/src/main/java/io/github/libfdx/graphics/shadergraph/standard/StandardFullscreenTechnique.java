package io.github.libfdx.graphics.shadergraph.standard;

import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.TextureFormat;

/**
 * Standard complete fullscreen and post-processing graph programs.
 *
 * <p>These assets are headless semantic graphs. Callers compile the returned
 * technique with {@link ShaderGraphTechniqueCompiler} and provide the source
 * texture/sampler declared by {@link #postProcessProgram()}.</p>
 */
public final class StandardFullscreenTechnique {
    private static final ShaderGraphType U32 =
            ShaderGraphType.scalar(ShaderScalarType.U32);
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private StandardFullscreenTechnique() {
    }

    /**
     * Creates a self-contained fullscreen UV/color visualization program.
     *
     * @return complete graph program
     */
    public static ShaderGraphProgram fullscreenProgram() {
        return ShaderGraphProgram.builder(
                        "libfdx.standard.fullscreen",
                        vertex(), gradientFragment())
                .build();
    }

    /**
     * Creates a sampled-texture Reinhard post-processing program.
     *
     * <p>The fragment graph declares a filterable 2D texture at group 0,
     * binding 0 and a filtering sampler at group 0, binding 1.</p>
     *
     * @return complete graph program
     */
    public static ShaderGraphProgram postProcessProgram() {
        return ShaderGraphProgram.builder(
                        "libfdx.standard.post-process",
                        vertex(), postFragment())
                .build();
    }

    /**
     * Creates a one-pass fullscreen technique for an exact target format.
     *
     * @param format color target format
     * @return immutable semantic technique
     */
    public static ShaderGraphTechnique fullscreen(
            TextureFormat format) {
        return technique("libfdx.standard.fullscreen",
                ShaderPassId.FORWARD, fullscreenProgram(), format);
    }

    /**
     * Creates a one-pass post-processing technique for an exact target format.
     *
     * @param format color target format
     * @return immutable semantic technique
     */
    public static ShaderGraphTechnique postProcess(
            TextureFormat format) {
        return technique("libfdx.standard.post-process",
                ShaderPassId.POST_PROCESS, postProcessProgram(),
                format);
    }

    private static ShaderGraphTechnique technique(String id,
            ShaderPassId passId, ShaderGraphProgram program,
            TextureFormat format) {
        ShaderGraphPipelineState state =
                ShaderGraphPipelineState.builder()
                        .primitive(PrimitiveState.triangles())
                        .colorTargets(ColorTargetState.opaque(format))
                        .build();
        ShaderGraphVariant variant = ShaderGraphVariant.builder(
                        "", program)
                .profiles(ShaderProfile.PORTABLE_WEBGPU,
                        ShaderProfile.PORTABLE_WEBGL2,
                        ShaderProfile.NATIVE)
                .build();
        return ShaderGraphTechnique.builder(id)
                .passes(ShaderGraphTechniquePass.builder(passId,
                                state)
                        .variants(variant)
                        .build())
                .build();
    }

    private static ShaderGraph vertex() {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "libfdx.standard.fullscreen.vertex",
                ShaderGraphKind.VERTEX);
        graph.parameter(ShaderGraphParameter.semantic(
                "vertex_index", U32,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.u32(0),
                "builtin.vertex_index"));
        ShaderExpression index = graph.parameter(
                "vertex_index_input", "vertex_index");
        ShaderExpression bottomLeft = graph.constant("bottom_left",
                vector2(-1, -1));
        ShaderExpression bottomRight = graph.constant("bottom_right",
                vector2(3, -1));
        ShaderExpression topLeft = graph.constant("top_left",
                vector2(-1, 3));
        ShaderExpression position2 = graph.switchValue(
                "fullscreen_position", index, bottomLeft,
                new long[] { 1, 2 }, bottomRight, topLeft);
        ShaderExpression x = graph.member("position_x", position2,
                "x", F32);
        ShaderExpression y = graph.member("position_y", position2,
                "y", F32);
        ShaderExpression zero = graph.floatValue(0);
        ShaderExpression one = graph.floatValue(1);
        ShaderExpression position = graph.construct(
                "clip_position", VEC4, x, y, zero, one);
        ShaderExpression half = graph.constant("half",
                vector2(0.5f, 0.5f));
        ShaderExpression uv = graph.multiply("uv_scale",
                graph.add("uv_bias", position2,
                        graph.constant("one2", vector2(1, 1))),
                half);
        graph.output("position", "builtin.position", position);
        graph.output("uv", "location.0", uv);
        return graph.build();
    }

    private static ShaderGraph gradientFragment() {
        ShaderGraphBuilder graph = fragmentBuilder(
                "libfdx.standard.fullscreen.fragment");
        ShaderExpression uv = graph.parameter("uv_input", "uv");
        ShaderExpression x = graph.member("uv_x", uv, "x", F32);
        ShaderExpression y = graph.member("uv_y", uv, "y", F32);
        ShaderExpression color = graph.construct("color", VEC4,
                x, y, graph.floatValue(0.25f),
                graph.floatValue(1));
        graph.output("color", "color0", color);
        return graph.build();
    }

    private static ShaderGraph postFragment() {
        ShaderGraphBuilder graph = fragmentBuilder(
                "libfdx.standard.post-process.fragment");
        graph.resource(ShaderGraphResource.of("source_texture",
                ShaderGraphType.texture(ShaderTextureDimension.D2,
                        ShaderTextureSampleType.FILTERABLE_FLOAT,
                        false),
                0, 0));
        graph.resource(ShaderGraphResource.of("source_sampler",
                ShaderGraphType.sampler(
                        ShaderSamplerKind.FILTERING),
                0, 1));
        ShaderExpression uv = graph.parameter("uv_input", "uv");
        ShaderExpression sampled = graph.sample2D("sample_source",
                graph.resource("source_texture_value",
                        "source_texture"),
                graph.resource("source_sampler_value",
                        "source_sampler"),
                uv);
        ShaderExpression rgb = graph.member("sample_rgb", sampled,
                "xyz", VEC3);
        ShaderExpression mapped = graph.call("reinhard",
                StandardShaderGraphs.REINHARD_TONE_MAP, rgb);
        ShaderExpression alpha = graph.member("sample_alpha",
                sampled, "w", F32);
        graph.output("color", "color0",
                graph.construct("output_color", VEC4,
                        graph.member("mapped_r", mapped, "x", F32),
                        graph.member("mapped_g", mapped, "y", F32),
                        graph.member("mapped_b", mapped, "z", F32),
                        alpha));
        return graph.build();
    }

    private static ShaderGraphBuilder fragmentBuilder(String id) {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(id,
                ShaderGraphKind.FRAGMENT);
        graph.parameter(ShaderGraphParameter.semantic("uv", VEC2,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.zero(VEC2), "location.0"));
        return graph;
    }

    private static ShaderGraphLiteral vector2(float x, float y) {
        return ShaderGraphLiteral.composite(VEC2,
                ShaderGraphLiteral.f32(x),
                ShaderGraphLiteral.f32(y));
    }
}
