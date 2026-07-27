package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.util.Locale;

/**
 * Deterministic semantic codec for complete render-technique assets.
 *
 * <p>Programs are embedded as immutable authoring dependencies. Their own
 * graph dependencies remain content-addressed and resolve through the same
 * headless graph library during compilation.</p>
 */
public final class ShaderGraphTechniqueCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphTechniqueCodec() {
    }

    public static String write(ShaderGraphTechnique technique) {
        if (technique == null) {
            throw new FdxException(
                    "Shader graph technique cannot be null");
        }
        JsonValue passes = JsonValue.array();
        for (ShaderGraphTechniquePass pass
                : technique.passes()) {
            passes.add(pass(pass));
        }
        return JsonWriter.compact(JsonValue.object()
                .put("asset", "technique")
                .put("format", CURRENT_VERSION)
                .put("id", technique.id())
                .put("maxVariants",
                        technique.maxVariants())
                .put("passes", passes));
    }

    public static ShaderGraphTechnique read(String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root == null || !root.isObject()
                || !"technique".equals(
                        root.requireString("asset"))
                || root.require("format").intValue()
                        != CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph technique asset format");
        }
        JsonValue passes = root.require("passes");
        requireArray(passes, "technique passes");
        ShaderGraphTechniquePass[] values =
                new ShaderGraphTechniquePass[passes.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = readPass(passes.require(i));
        }
        return ShaderGraphTechnique.builder(
                        root.requireString("id"))
                .maxVariants(
                        root.require("maxVariants").intValue())
                .passes(values)
                .build();
    }

    private static JsonValue pass(
            ShaderGraphTechniquePass pass) {
        JsonValue variants = JsonValue.array();
        for (ShaderGraphVariant variant : pass.variants()) {
            variants.add(variant(variant));
        }
        return JsonValue.object()
                .put("id", pass.passId().value())
                .put("pipelineState",
                        ShaderGraphPipelineStateCodec.value(
                                pass.pipelineState()))
                .put("defaultVariant",
                        pass.defaultVariantKey())
                .put("variants", variants);
    }

    private static ShaderGraphTechniquePass readPass(
            JsonValue value) {
        JsonValue variants = value.require("variants");
        requireArray(variants, "technique variants");
        ShaderGraphVariant[] values =
                new ShaderGraphVariant[variants.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = readVariant(variants.require(i));
        }
        return ShaderGraphTechniquePass.builder(
                        ShaderPassId.of(
                                value.requireString("id")),
                        ShaderGraphPipelineStateCodec.readValue(
                                value.require("pipelineState")))
                .defaultVariant(
                        value.requireString("defaultVariant"))
                .variants(values)
                .build();
    }

    private static JsonValue variant(
            ShaderGraphVariant variant) {
        JsonValue staticValues = JsonValue.array();
        for (ShaderGraphStaticValue value
                : variant.staticValues()) {
            staticValues.add(JsonValue.object()
                    .put("parameter",
                            value.parameterId().value())
                    .put("value", value.boolValue()));
        }
        JsonValue profiles = JsonValue.array();
        for (ShaderProfile profile : variant.profiles()) {
            profiles.add(JsonValue.value(profile.id()));
        }
        JsonValue features = JsonValue.array();
        for (GraphicsFeature feature : variant.features()) {
            features.add(JsonValue.value(feature.name()
                    .toLowerCase(Locale.ROOT)));
        }
        JsonValue result = JsonValue.object()
                .put("key", variant.key())
                .put("program", new JsonReader().parse(
                        ShaderGraphProgramCodec.write(
                                variant.sourceProgram())))
                .put("staticValues", staticValues)
                .put("profiles", profiles)
                .put("features", features);
        result.put("fallback",
                variant.fallbackKey() != null
                        ? JsonValue.value(variant.fallbackKey())
                        : JsonValue.nullValue());
        return result;
    }

    private static ShaderGraphVariant readVariant(
            JsonValue value) {
        JsonValue staticValues = value.require("staticValues");
        JsonValue profiles = value.require("profiles");
        JsonValue features = value.require("features");
        requireArray(staticValues, "static values");
        requireArray(profiles, "profiles");
        requireArray(features, "features");
        ShaderGraphStaticValue[] switches =
                new ShaderGraphStaticValue[staticValues.size()];
        for (int i = 0; i < switches.length; i++) {
            JsonValue item = staticValues.require(i);
            switches[i] = ShaderGraphStaticValue.bool(
                    item.requireString("parameter"),
                    item.require("value").booleanValue());
        }
        ShaderProfile[] profileValues =
                new ShaderProfile[profiles.size()];
        for (int i = 0; i < profileValues.length; i++) {
            profileValues[i] = ShaderProfile.fromId(
                    profiles.require(i).stringValue(),
                    ShaderProfile.PORTABLE_WEBGPU);
        }
        GraphicsFeature[] featureValues =
                new GraphicsFeature[features.size()];
        for (int i = 0; i < featureValues.length; i++) {
            try {
                featureValues[i] = GraphicsFeature.valueOf(
                        features.require(i).stringValue()
                                .toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new FdxException(
                        "Unknown shader graph feature: "
                                + features.require(i).stringValue(),
                        exception);
            }
        }
        ShaderGraphVariant.Builder builder =
                ShaderGraphVariant.builder(
                                value.requireString("key"),
                                ShaderGraphProgramCodec.read(
                                        JsonWriter.compact(
                                                value.require(
                                                        "program"))))
                        .staticValues(switches)
                        .profiles(profileValues)
                        .features(featureValues);
        JsonValue fallback = value.require("fallback");
        if (!fallback.isNull()) {
            builder.fallback(fallback.stringValue());
        }
        return builder.build();
    }

    private static void requireArray(JsonValue value,
            String label) {
        if (!value.isArray()) {
            throw new FdxException(
                    "Shader graph " + label
                            + " must be an array");
        }
    }
}
