package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Defines the code-authored and serialized surface used by the public sample.
 */
public final class ShaderGraphSampleGraphs {
    public static final String SURFACE_ASSET =
            "shaders/surface-effects.fdxgraph";

    /** The file and Java versions of each playable example. */
    public enum Example {
        DISSOLVE("Dissolve and rim", SURFACE_ASSET, "threshold", 0.05f, 0.80f, ShaderGraphSampleGraphs::codeAuthoredSurface),
        BASALT("Molten basalt", "shaders/molten-basalt.fdxgraph", "flow_phase", 0, 1, ShaderGraphSampleGraphs::moltenBasalt),
        VORTEX("Vortex portal", "shaders/vortex-portal.fdxgraph", "orbit_phase", 0.0f, 1.0f, ShaderGraphSampleGraphs::vortexPortal);

        public final String title;
        public final String asset;
        public final ShaderGraphPreviewAnimation animation;
        private final Supplier<ShaderGraph> builder;

        Example(String title, String asset, String animatedParameter, float low, float high, Supplier<ShaderGraph> builder) {
            this.title = title;
            this.asset = asset;
            this.animation = new ShaderGraphPreviewAnimation(animatedParameter, low, high);
            this.builder = builder;
        }

        public ShaderGraphDocument document() {
            return ShaderGraphDocument.of(build()).withEditorJson(JsonWriter.compact(animation.writeTo(JsonValue.object())));
        }

        public ShaderGraph build() {
            return builder.get();
        }

    }

    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);

    private ShaderGraphSampleGraphs() {
    }

    /**
     * Builds the sample's masked and emissive PBR surface. The threshold and rim strength
     * are per-material so the two meshes can share one shader.
     */
    public static ShaderGraph codeAuthoredSurface() {
        ShaderGraphBuilder graph = new ShaderGraphBuilder("sample.surface-effects", ShaderGraphKind.SURFACE);
        input(graph, "alpha", F32, "alpha");
        input(graph, "uv", VEC2, "uv0");
        input(graph, "normal", VEC3, "normal");
        input(graph, "view", VEC3, "viewDirection");
        input(graph, "emissive", VEC3, "emissive");
        scalarMaterial(graph, "threshold", 0.36f);
        scalarMaterial(graph, "rim_strength", 0.80f);
        scalarMaterial(graph, "rim_power", 3.0f);

        ShaderExpression uv = graph.parameter("surface_uv", "uv");
        // Each point has a fixed burn delay. The front eats through the surface and exposes it again behind the ash.
        var grain = graph.noise2D("burn_grain", scaledUv(graph, "grain_uv", uv, 16, 9));
        var height = graph.dot("surface_height", uv, vector2(graph, "height_axis", 0, 0.32f));
        var delay = graph.add("burn_delay", height, graph.multiply("ragged_front", grain, graph.floatValue(0.34f)));
        var cycles = graph.divide("burn_cycles", graph.time("time"), graph.floatValue(6));
        var age = graph.fraction("burn_age", graph.subtract("local_burn_time",
                graph.add("start_phase", cycles, graph.floatValue(0.43f)), delay));
        var distance = graph.absolute("distance_to_ash", graph.subtract("burn_front", age, graph.floatValue(0.5f)));
        var width = graph.multiply("ash_width", graph.parameter("dissolve_threshold", "threshold"), graph.floatValue(0.62f));
        var coverage = graph.smoothstep("coverage", width, graph.add("edge_end", width, graph.floatValue(0.045f)), distance);
        graph.output("alpha", "alpha", graph.multiply("masked_alpha", graph.parameter("surface_alpha", "alpha"), coverage));

        // Masked fragments disappear. On the surviving side, fade the hot edge back to the base material.
        var edge = graph.subtract("hot_edge", graph.floatValue(1), graph.smoothstep("edge_falloff", width,
                graph.add("ember_width", width, graph.floatValue(0.094f)), distance));
        ShaderExpression ember = scaledColor(graph, "ember", edge, 3.4f, 0.55f, 0.04f);
        ShaderExpression rim = graph.fresnel("facing_rim", graph.parameter("surface_normal", "normal"),
                graph.parameter("surface_to_eye", "view"), graph.parameter("falloff", "rim_power"));
        ShaderExpression intensity = graph.multiply("rim_intensity", rim, graph.parameter("strength", "rim_strength"));
        ShaderExpression halo = scaledColor(graph, "cyan_rim", intensity, 0.10f, 0.85f, 1.8f);
        graph.output("emissive", "emissive", graph.add("glow", graph.parameter("surface_emission", "emissive"),
                graph.add("effect_emission", ember, halo)));
        return graph.build();
    }

    /** Stationary basalt plates with incandescent lava flowing through the fissures between them. */
    public static ShaderGraph moltenBasalt() {
        var graph = new ShaderGraphBuilder("sample.molten-basalt", ShaderGraphKind.SURFACE);
        input(graph, "uv", VEC2, "uv0");
        scalarMaterial(graph, "heat", 0.55f);
        scalarMaterial(graph, "flow_phase", 0);
        var uv = graph.parameter("surface_uv", "uv");
        var u = graph.dot("longitude", uv, vector2(graph, "u_axis", 1, 0));
        var seam = graph.smoothstep("longitude_seam", graph.floatValue(0.68f), graph.floatValue(1), u);

        // Where two plates meet, their noise fields have equal strength. The third field clips that
        // boundary at a junction. All three fields use mesh UVs, so the rock stays in place.
        var grain = basaltNoise(graph, "stone_grain", scaledUv(graph, "grain_uv", uv, 46, 23), 46, seam);
        var plates = scaledUv(graph, "plate_uv", uv, 11, 5.5f);
        var shear = graph.multiply("jagged_edges", grain, graph.floatValue(0.44f));
        var bed = graph.add("fractured_bed", plates, graph.construct("shear_offset", VEC2, shear, graph.negate("cross_shear", shear)));
        var a = basaltNoise(graph, "plate_a", bed, 11, seam);
        var b = basaltNoise(graph, "plate_b", graph.add("second_stratum", bed, vector2(graph, "offset_b", 13.1f, 8.7f)), 11, seam);
        var c = basaltNoise(graph, "plate_c", graph.add("third_stratum", bed, vector2(graph, "offset_c", -7.4f, 21.3f)), 11, seam);
        var ab = graph.maximum("stronger_plate", a, b);
        var strongest = graph.maximum("dominant_plate", ab, c);
        var runnerUp = graph.maximum("adjacent_plate", graph.minimum("weaker_plate", a, b), graph.minimum("third_boundary", ab, c));
        var distance = graph.subtract("distance_to_fissure", strongest, runnerUp);
        var width = graph.lerp("opening_width", graph.floatValue(0.032f), graph.floatValue(0.15f),
                graph.parameter("magma_heat", "heat"));
        var molten = graph.subtract("exposed_magma", graph.floatValue(1),
                graph.smoothstep("rock_banks", graph.floatValue(0), width, distance));

        // A crest travels along the fissures. One turn brings every point back to its starting
        // temperature with the same direction of travel, including when the phase slider wraps.
        var rate = graph.constant("cycles_per_second", ShaderGraphLiteral.f32(1f / 6f));
        var turns = graph.add("flow_turns", graph.multiply("elapsed_cycles", graph.time("time"), rate),
                graph.parameter("phase_offset", "flow_phase"));
        var slope = graph.dot("downhill", uv, vector2(graph, "flow_direction", 1, 2.6f));
        var path = graph.add("fissure_path", slope, graph.multiply("flow_bend", a, graph.floatValue(0.48f)));
        var angle = graph.multiply("flow_angle", graph.subtract("travelling_crest", path, graph.fraction("cycle_phase", turns)),
                graph.floatValue(6.2831855f));
        var hot = graph.smoothstep("hot_front", graph.floatValue(-0.75f), graph.floatValue(0.85f), graph.cosine("current", angle));
        var pores = basaltNoise(graph, "pores", scaledUv(graph, "pore_uv", uv, 165, 82.5f), 165, seam);
        var fold = graph.add("fold_angle", graph.multiply("overtaking_current", angle, graph.floatValue(2)),
                graph.multiply("broken_filaments", grain, graph.floatValue(7)));
        var striation = graph.lerp("liquid_folds", graph.floatValue(0.43f), graph.floatValue(1),
                graph.smoothstep("fold_crest", graph.floatValue(-0.7f), graph.floatValue(0.7f), graph.cosine("ripple", fold)));
        var center = graph.multiply("fissure_core", molten, molten);
        var fire = graph.multiply("liquid_core", center, graph.multiply("flowing_heat", hot, striation));

        var grit = graph.smoothstep("rough_aggregate", graph.floatValue(0.15f), graph.floatValue(0.85f),
                graph.lerp("volcanic_grit", pores, grain, graph.floatValue(0.35f)));
        var rock = blendColor(graph, "porous_basalt", vector(graph, "charcoal", 0.027f, 0.024f, 0.025f),
                vector(graph, "weathered_stone", 0.089f, 0.078f, 0.073f), grit);
        graph.output("color", "baseColor", rock);
        graph.output("roughness", "roughness", graph.lerp("rock_to_liquid", graph.floatValue(0.96f), graph.floatValue(0.38f), molten));
        graph.output("emission", "emissive", graph.add("magma_glow",
                scaledColor(graph, "embers", molten, 0.78f, 0.037f, 0.003f),
                scaledColor(graph, "incandescent_core", fire, 3.2f, 0.94f, 0.095f)));
        return graph.build();
    }

    /** Crossfades adjacent copies of a noise field at the mesh's longitude seam. */
    private static ShaderExpression basaltNoise(ShaderGraphBuilder graph, String id, ShaderExpression uv,
            float width, ShaderExpression seam) {
        var adjacent = graph.subtract(id + "_wrapped_uv", uv, vector2(graph, id + "_tile_width", width, 0));
        return graph.lerp(id, graph.noise2D(id + "_noise", uv), graph.noise2D(id + "_neighbor", adjacent), seam);
    }

    /** An emissive whirlpool. One unit of orbit_phase rotates the streams and sparks through a full turn. */
    public static ShaderGraph vortexPortal() {
        var graph = new ShaderGraphBuilder("sample.vortex-portal", ShaderGraphKind.SURFACE);
        input(graph, "uv", VEC2, "uv0");
        scalarMaterial(graph, "orbit_phase", 0.18f);
        scalarMaterial(graph, "twist", 10.5f);
        var uv = graph.parameter("surface_uv", "uv");
        var one = graph.floatValue(1);
        var quarterTurn = graph.floatValue(1.5707963f);

        // Project the longitude and latitude onto a disk. Both sides of the UV seam meet at the same point.
        var longitude = graph.dot("longitude", uv, vector2(graph, "longitude_axis", 6.283185f, 0));
        var latitude = graph.dot("latitude", uv, vector2(graph, "latitude_axis", 0, 3.1415927f));
        var latitudeRing = graph.cosine("latitude_ring", graph.subtract("latitude_from_equator", latitude, quarterTurn));
        var meridian = graph.add("portal_longitude", longitude, graph.floatValue(0.45f));
        var disk = graph.construct("portal_disk", VEC2, graph.multiply("across", graph.cosine("meridian", meridian), latitudeRing),
                graph.subtract("height_above_eye", graph.cosine("elevation", latitude), graph.floatValue(0.32f)));
        var radius2 = graph.dot("radius_squared", disk, disk);

        // Inner orbits wind more tightly. Rotating the sampling axes twists straight streams into spiral arms.
        var winding = graph.divide("winding_curve", radius2, graph.add("bend_radius", radius2, graph.floatValue(0.28f)));
        var turns = graph.add("orbit_turns", graph.divide("elapsed_turns", graph.time("time"), graph.floatValue(6)),
                graph.parameter("orbit_offset", "orbit_phase"));
        var spin = graph.multiply("orbit_angle", turns, graph.floatValue(6.283185f));
        var angle = graph.add("stream_angle", spin, graph.multiply("radial_twist", winding, graph.parameter("winding", "twist")));
        var cos = graph.cosine("orbit_cosine", angle);
        var sin = graph.cosine("orbit_sine", graph.subtract("quarter_turn_offset", angle, quarterTurn));
        var along = graph.dot("along_stream", disk, graph.construct("orbit_axis", VEC2, cos, sin));
        var across = graph.dot("across_stream", disk, graph.construct("cross_axis", VEC2, graph.negate("negative_sine", sin), cos));
        var flow = graph.construct("orbit_coordinates", VEC2, along, across);
        var dust = graph.noise2D("turbulence", scaledUv(graph, "turbulent_flow", flow, 8, 14));
        var drift = graph.multiply("stream_drift", graph.subtract("centered_noise", dust, graph.floatValue(0.5f)), graph.floatValue(0.15f));
        var stream = graph.add("stream_offset", across, drift);
        var distance = graph.absolute("distance_to_stream", stream);
        var vapor = brightBand(graph, "violet_nebula", distance, 0.04f, 0.37f);
        var mist = graph.multiply("nebula_clouds", vapor, graph.lerp("cloud_density", graph.floatValue(0.30f), one, dust));
        var filament = brightBand(graph, "plasma_filament", distance, 0.004f, 0.047f);
        var wake = brightBand(graph, "trailing_filament", graph.absolute("wake_distance",
                graph.add("wake_offset", stream, graph.floatValue(0.115f))), 0.002f, 0.028f);

        var armColor = blendColor(graph, "opposing_streams", vector(graph, "ion_blue", 0.11f, 0.54f, 1.0f),
                vector(graph, "hot_magenta", 0.98f, 0.10f, 0.50f), graph.smoothstep("stream_side",
                        graph.floatValue(-0.23f), graph.floatValue(0.23f), along));
        var plasma = graph.multiply("plasma", armColor, graph.construct("filament_rgb", VEC3, filament, filament, filament));
        var streams = graph.add("streams", plasma, graph.add("scattered_light",
                scaledColor(graph, "nebula", mist, 0.30f, 0.044f, 0.62f),
                scaledColor(graph, "afterglow", wake, 0.23f, 0.15f, 0.52f)));
        var specks = graph.noise2D("orbiting_dust", scaledUv(graph, "dust_cloud", flow, 48, 54));
        var sparks = graph.smoothstep("bright_sparks", graph.floatValue(0.79f), graph.floatValue(0.94f), specks);
        var orbitLight = graph.add("streams_and_sparks", streams, scaledColor(graph, "starlight", sparks, 0.45f, 0.65f, 0.88f));

        var eye = graph.smoothstep("eye_mask", graph.floatValue(0.039f), graph.floatValue(0.054f), radius2);
        var diskMask = graph.multiply("luminous_disk", eye, brightBand(graph, "outer_falloff", radius2, 0.60f, 1.04f));
        var diskLight = graph.multiply("disk_light", orbitLight, graph.construct("disk_rgb", VEC3, diskMask, diskMask, diskMask));
        var rim = graph.multiply("photon_ring", eye, brightBand(graph, "ring_falloff", radius2, 0.054f, 0.088f));
        graph.output("emission", "emissive", graph.add("vortex_light", diskLight, scaledColor(graph, "inner_rim", rim, 0.48f, 0.54f, 0.96f)));
        graph.output("color", "baseColor", vector(graph, "deep_space", 0.012f, 0.010f, 0.035f));
        graph.output("roughness", "roughness", one);
        return graph.build();
    }

    private static ShaderExpression brightBand(ShaderGraphBuilder graph, String id, ShaderExpression distance,
            float inner, float outer) {
        return graph.subtract(id, graph.floatValue(1), graph.smoothstep(id + "_fade",
                graph.floatValue(inner), graph.floatValue(outer), distance));
    }

    private static ShaderExpression scaledUv(ShaderGraphBuilder graph, String id, ShaderExpression uv, float u, float v) {
        return graph.multiply(id, uv, vector2(graph, id + "_scale", u, v));
    }

    private static ShaderExpression vector2(ShaderGraphBuilder graph, String id, float x, float y) {
        return graph.constant(id, ShaderGraphLiteral.composite(VEC2, ShaderGraphLiteral.f32(x), ShaderGraphLiteral.f32(y)));
    }

    private static ShaderExpression blendColor(ShaderGraphBuilder graph, String id, ShaderExpression start,
            ShaderExpression end, ShaderExpression weight) {
        return graph.lerp(id, start, end, graph.construct(id + "_weight", VEC3, weight, weight, weight));
    }

    private static ShaderExpression vector(ShaderGraphBuilder graph, String id, float x, float y, float z) {
        return graph.constant(id, ShaderGraphLiteral.composite(VEC3,
                ShaderGraphLiteral.f32(x), ShaderGraphLiteral.f32(y), ShaderGraphLiteral.f32(z)));
    }

    private static void scalarMaterial(ShaderGraphBuilder graph, String id, float initial) {
        graph.parameter(ShaderGraphParameter.of(id, F32, ShaderGraphParameterKind.MATERIAL, ShaderGraphLiteral.f32(initial)));
    }

    private static ShaderExpression scaledColor(ShaderGraphBuilder graph, String id, ShaderExpression intensity,
            float r, float g, float b) {
        ShaderExpression color = graph.constant(id + "_color", ShaderGraphLiteral.composite(VEC3,
                ShaderGraphLiteral.f32(r), ShaderGraphLiteral.f32(g), ShaderGraphLiteral.f32(b)));
        return graph.multiply(id, color, graph.construct(id + "_intensity", VEC3, intensity, intensity, intensity));
    }

    private static void input(ShaderGraphBuilder graph, String id,
            ShaderGraphType type, String semantic) {
        graph.parameter(ShaderGraphParameter.semantic(id, type,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.zero(type), semantic));
    }

    /** Exports every example to the directory supplied by the Gradle generation task. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected one output directory");
        }
        Path directory = Files.createDirectories(Path.of(args[0]));
        for (Example example : Example.values()) {
            Path output = directory.resolve(Path.of(example.asset).getFileName());
            String document = ShaderGraphDocumentCodec.write(example.document());
            Files.writeString(output, document + System.lineSeparator(), StandardCharsets.UTF_8);
            System.out.println("Generated " + output);
        }
    }

}
