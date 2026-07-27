package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;

import java.nio.charset.StandardCharsets;

/**
 * Defines the code-authored and serialized surface used by the public sample.
 *
 * <p>This class belongs to the headless core sample and has no UI Kit
 * dependency. {@link #codeAuthoredSurface()} is the direct Java authoring
 * path; {@link #loadSurface(FileSystem)} reads the equivalent
 * {@code .fdxgraph} asset.</p>
 */
public final class ShaderGraphSampleGraphs {
    public static final String SURFACE_ASSET =
            "shaders/warm-pbr-surface.fdxgraph";
    public static final String SURFACE_LOCAL_PATH =
            "assets/" + SURFACE_ASSET;

    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private ShaderGraphSampleGraphs() {
    }

    /**
     * Builds a complete replacement for the standard PBR surface graph.
     *
     * <p>The graph preserves the renderer-owned PBR input/output contract and
     * adds the material-owned {@code warmth} parameter. ModelBatch continues
     * to own cameras, lights, shadows, textures, and draw submission.</p>
     *
     * @return immutable code-authored surface graph
     */
    public static ShaderGraph codeAuthoredSurface() {
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "sample.warm-pbr.surface", ShaderGraphKind.SURFACE);
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
                vector(VEC4, 1.0f, 1.0f, 1.0f, 1.0f)));
        graph.parameter(ShaderGraphParameter.of("emissive_gain", F32,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.f32(1.0f)));
        graph.parameter(ShaderGraphParameter.of("warmth", F32,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.f32(0.35f)));

        ShaderExpression base =
                graph.parameter("base_input", "base_color");
        ShaderExpression alpha =
                graph.parameter("alpha_input", "alpha");
        ShaderExpression normal =
                graph.parameter("normal_input", "normal");
        ShaderExpression metallic =
                graph.parameter("metallic_input", "metallic");
        ShaderExpression roughness =
                graph.parameter("roughness_input", "roughness");
        ShaderExpression occlusion =
                graph.parameter("occlusion_input", "occlusion");
        ShaderExpression emissive =
                graph.parameter("emissive_input", "emissive");
        graph.parameter("uv_input", "uv");
        ShaderExpression tint = graph.parameter("tint_input", "tint");
        ShaderExpression tintRgb =
                graph.member("tint_rgb", tint, "xyz", VEC3);
        ShaderExpression tintAlpha =
                graph.member("tint_alpha", tint, "w", F32);
        ShaderExpression warmth =
                graph.parameter("warmth_input", "warmth");
        ShaderExpression one = graph.constant(
                "one", ShaderGraphLiteral.f32(1.0f));
        ShaderExpression redWarmth = graph.multiply(
                "red_warmth", warmth,
                graph.constant("red_warmth_scale",
                        ShaderGraphLiteral.f32(0.20f)));
        ShaderExpression blueWarmth = graph.multiply(
                "blue_warmth", warmth,
                graph.constant("blue_warmth_scale",
                        ShaderGraphLiteral.f32(0.15f)));
        ShaderExpression warmTint = graph.construct(
                "warm_tint", VEC3,
                graph.add("warm_red", one, redWarmth),
                one,
                graph.subtract("warm_blue", one, blueWarmth));
        ShaderExpression gain =
                graph.parameter("emissive_gain_input", "emissive_gain");
        ShaderExpression gain3 = graph.construct(
                "emissive_gain3", VEC3, gain, gain, gain);

        ShaderExpression tintedBase =
                graph.multiply("tinted_base", base, tintRgb);
        graph.output("base_color", "baseColor",
                graph.multiply("warm_base", tintedBase, warmTint));
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

    /**
     * Loads the checked-in semantic asset through the portable file service.
     *
     * @param files active file service
     * @return decoded surface graph
     */
    public static ShaderGraph loadSurface(FileSystem files) {
        ShaderGraphDocument document =
                ShaderGraphDocumentCodec.read(loadSurfaceSource(files));
        if (document.graph() == null) {
            throw new FdxException("Shader graph sample asset must contain a graph");
        }
        return document.graph();
    }

    /**
     * Loads the checked-in semantic JSON.
     *
     * @param files active file service
     * @return graph JSON
     */
    public static String loadSurfaceSource(FileSystem files) {
        if (files == null) {
            throw new FdxException("Shader graph sample requires a file service");
        }
        try {
            return files.internal(SURFACE_ASSET)
                    .readString(StandardCharsets.UTF_8).get();
        } catch (RuntimeException failure) {
            throw new FdxException("Could not read shader graph sample asset "
                    + SURFACE_ASSET, failure);
        }
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
