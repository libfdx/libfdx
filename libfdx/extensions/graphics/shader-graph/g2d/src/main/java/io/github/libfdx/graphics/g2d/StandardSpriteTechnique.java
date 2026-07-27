package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;

/**
 * Standard graph-generated SpriteBatch programs for every public
 * {@link SpriteShaderAbi}.
 *
 * <p>The technique defines shader programs and fixed state only. SpriteBatch
 * retains packing, resource binding, upload, flush, and draw ownership.</p>
 */
public final class StandardSpriteTechnique {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType U32 =
            ShaderGraphType.scalar(ShaderScalarType.U32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);
    private static final ShaderGraphType TEXTURE =
            ShaderGraphType.texture(ShaderTextureDimension.D2,
                    ShaderTextureSampleType.FILTERABLE_FLOAT, false);
    private static final ShaderGraphType SAMPLER =
            ShaderGraphType.sampler(ShaderSamplerKind.FILTERING);

    private StandardSpriteTechnique() {
    }

    /**
     * Creates the complete standard sprite technique for one render target.
     *
     * @param colorFormat target color format
     * @param sampleCount target sample count
     * @return semantic technique
     */
    public static ShaderGraphTechnique create(TextureFormat colorFormat,
            int sampleCount) {
        if (colorFormat == null || colorFormat == TextureFormat.UNKNOWN
                || colorFormat.isDepthStencil()) {
            throw new FdxException(
                    "Standard sprite technique requires a color format");
        }
        ShaderGraphTechniquePass[] passes =
                new ShaderGraphTechniquePass[SpriteShaderAbi.values().length];
        int index = 0;
        for (SpriteShaderAbi abi : SpriteShaderAbi.values()) {
            ShaderGraphProgram program = program(abi);
            ShaderGraphPipelineState state =
                    ShaderGraphPipelineState.builder()
                            .colorTargets(ColorTargetState.alpha(
                                    colorFormat))
                            .multisample(MultisampleState.of(
                                    sampleCount, -1, false))
                            .vertexLayouts(abi.vertexLayouts())
                            .build();
            passes[index++] = ShaderGraphTechniquePass.builder(
                            abi.passId(), state)
                    .variants(ShaderGraphVariant.builder("", program)
                            .build())
                    .build();
        }
        return ShaderGraphTechnique.builder(
                        "libfdx.standard.sprite")
                .passes(passes)
                .build();
    }

    /**
     * Compiles a standard technique for the current surface and device
     * capabilities.
     *
     * @param graphics graphics context
     * @return atomic technique compilation result
     */
    public static ShaderGraphTechniqueCompileResult compile(
            GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        ShaderProfile profile = preferredProfile(graphics);
        return new ShaderGraphTechniqueCompiler().compile(
                create(graphics.surfaceFormat(), 1),
                ShaderGraphCompileOptions.builder()
                        .profile(profile)
                        .capabilities(
                                graphics.device().capabilities())
                        .build());
    }

    private static ShaderProfile preferredProfile(
            GraphicsContext graphics) {
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGPU)) {
            return ShaderProfile.PORTABLE_WEBGPU;
        }
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGL2)) {
            return ShaderProfile.PORTABLE_WEBGL2;
        }
        return ShaderProfile.NATIVE;
    }

    private static ShaderGraphProgram program(SpriteShaderAbi abi) {
        ShaderGraph vertex;
        if (abi == SpriteShaderAbi.ORDINARY
                || abi == SpriteShaderAbi.ORDINARY_INDEXED) {
            vertex = ordinaryVertex(false, abi);
        } else if (abi == SpriteShaderAbi.WHITE
                || abi == SpriteShaderAbi.WHITE_INDEXED) {
            vertex = ordinaryVertex(true, abi);
        } else if (abi == SpriteShaderAbi.PACKED_INSTANCED
                || abi == SpriteShaderAbi.PACKED_INSTANCED_INDEXED) {
            vertex = packedVertex(abi);
        } else {
            vertex = compactVertex(abi);
        }
        return ShaderGraphProgram.builder(
                        "libfdx.standard." + abi.passId().value(),
                        vertex, fragment(abi.white(), abi))
                .entryPoints("spriteVertex", "spriteFragment")
                .build();
    }

    private static ShaderGraph ordinaryVertex(boolean white,
            SpriteShaderAbi abi) {
        ShaderGraphBuilder graph = vertex(abi);
        stageInput(graph, "position", VEC2, 0);
        stageInput(graph, "uv", VEC2, 1);
        if (!white) {
            stageInput(graph, "color", VEC4, 2);
        }
        ShaderExpression position = graph.parameter(
                "position_input", "position");
        graph.output("position", ShaderGraphStageSemantic.POSITION,
                graph.construct("clip_position", VEC4,
                        position, graph.floatValue(0),
                        graph.floatValue(1)));
        graph.output("uv", ShaderGraphStageSemantic.location(0),
                graph.parameter("uv_input", "uv"));
        if (!white) {
            graph.output("color",
                    ShaderGraphStageSemantic.location(1),
                    graph.parameter("color_input", "color"));
        }
        return graph.build();
    }

    private static ShaderGraph packedVertex(SpriteShaderAbi abi) {
        ShaderGraphBuilder graph = vertex(abi);
        graph.parameter(ShaderGraphParameter.semantic(
                "vertex_index", U32,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.VERTEX_INDEX));
        stageInput(graph, "base_edge_x", VEC4, 0);
        stageInput(graph, "edge_y_uv_base", VEC4, 1);
        stageInput(graph, "uv_size_color_rg", VEC4, 2);
        stageInput(graph, "color_ba", VEC2, 3);
        ShaderExpression vertexIndex = graph.parameter(
                "vertex_index_input", "vertex_index");
        String cornerX = abi.indexed()
                ? "select(0.0, 1.0, $0 == 2u || $0 == 3u)"
                : "select(0.0, 1.0, $0 == 2u || $0 == 4u || $0 == 5u)";
        String cornerY = abi.indexed()
                ? "select(0.0, 1.0, $0 == 1u || $0 == 2u)"
                : "select(0.0, 1.0, $0 == 1u || $0 == 2u || $0 == 4u)";
        ShaderExpression x = graph.customWgsl(
                "corner_x", F32, cornerX, vertexIndex);
        ShaderExpression y = graph.customWgsl(
                "corner_y", F32, cornerY, vertexIndex);
        ShaderExpression base = graph.parameter(
                "base_edge_x_input", "base_edge_x");
        ShaderExpression edgeUv = graph.parameter(
                "edge_y_uv_base_input", "edge_y_uv_base");
        ShaderExpression uvColor = graph.parameter(
                "uv_size_color_rg_input", "uv_size_color_rg");
        ShaderExpression ba = graph.parameter(
                "color_ba_input", "color_ba");
        ShaderExpression position = graph.customWgsl(
                "position_value", VEC2,
                "$0.xy + $0.zw * $2 + $1.xy * $3",
                base, edgeUv, x, y);
        graph.output("position", ShaderGraphStageSemantic.POSITION,
                graph.construct("clip_position", VEC4, position,
                        graph.floatValue(0), graph.floatValue(1)));
        graph.output("uv", ShaderGraphStageSemantic.location(0),
                graph.customWgsl("uv_value", VEC2,
                        "$0.zw + $1.xy * vec2<f32>($2, $3)",
                        edgeUv, uvColor, x, y));
        graph.output("color", ShaderGraphStageSemantic.location(1),
                graph.customWgsl("color_value", VEC4,
                        "vec4<f32>($0.zw, $1)",
                        uvColor, ba));
        return graph.build();
    }

    private static ShaderGraph compactVertex(SpriteShaderAbi abi) {
        ShaderGraphBuilder graph = vertex(abi);
        stageInput(graph, "local_position", VEC2, 0);
        stageInput(graph, "uv", VEC2, 1);
        stageInput(graph, "color", VEC4, 2);
        stageInput(graph, "center", VEC2, 3);
        ShaderExpression position = graph.add("world_position",
                graph.parameter("center_input", "center"),
                graph.parameter("position_input", "local_position"));
        graph.output("position", ShaderGraphStageSemantic.POSITION,
                graph.construct("clip_position", VEC4, position,
                        graph.floatValue(0), graph.floatValue(1)));
        graph.output("uv", ShaderGraphStageSemantic.location(0),
                graph.parameter("uv_input", "uv"));
        graph.output("color", ShaderGraphStageSemantic.location(1),
                graph.parameter("color_input", "color"));
        return graph.build();
    }

    private static ShaderGraph fragment(boolean white,
            SpriteShaderAbi abi) {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "libfdx.standard." + abi.passId().value()
                        + ".fragment",
                ShaderGraphKind.FRAGMENT);
        graph.parameter(ShaderGraphParameter.semantic(
                "uv", VEC2,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.location(0)));
        if (!white) {
            graph.parameter(ShaderGraphParameter.semantic(
                    "color", VEC4,
                    ShaderGraphParameterKind.STAGE_INPUT, null,
                    ShaderGraphStageSemantic.location(1)));
        }
        graph.resource(ShaderGraphResource.of(
                "texture", TEXTURE, 0, 0));
        graph.resource(ShaderGraphResource.of(
                "sampler", SAMPLER, 0, 1));
        ShaderExpression sampled = graph.sample2D("sample",
                graph.resource("texture_resource", "texture"),
                graph.resource("sampler_resource", "sampler"),
                graph.parameter("uv_input", "uv"));
        ShaderExpression color = white ? sampled
                : graph.multiply("tinted_sample", sampled,
                        graph.parameter("color_input", "color"));
        graph.output("color", ShaderGraphStageSemantic.location(0),
                color);
        return graph.build();
    }

    private static ShaderGraphBuilder vertex(SpriteShaderAbi abi) {
        return new ShaderGraphBuilder(
                "libfdx.standard." + abi.passId().value()
                        + ".vertex",
                ShaderGraphKind.VERTEX);
    }

    private static void stageInput(ShaderGraphBuilder graph,
            String id, ShaderGraphType type, int location) {
        graph.parameter(ShaderGraphParameter.semantic(
                id, type, ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.location(location)));
    }
}
