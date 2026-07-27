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
 * Deterministic semantic codec for complete compute-technique assets.
 */
public final class ShaderGraphComputeTechniqueCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphComputeTechniqueCodec() {
    }

    public static String write(
            ShaderGraphComputeTechnique technique) {
        if (technique == null) {
            throw new FdxException(
                    "Shader graph compute technique cannot be null");
        }
        JsonValue passes = JsonValue.array();
        for (ShaderGraphComputeTechniquePass pass
                : technique.passes()) {
            JsonValue variants = JsonValue.array();
            for (ShaderGraphComputeVariant variant
                    : pass.variants()) {
                variants.add(variant(variant));
            }
            passes.add(JsonValue.object()
                    .put("id", pass.passId().value())
                    .put("defaultVariant",
                            pass.defaultVariantKey())
                    .put("variants", variants));
        }
        return JsonWriter.compact(JsonValue.object()
                .put("asset", "compute-technique")
                .put("format", CURRENT_VERSION)
                .put("id", technique.id())
                .put("maxVariants",
                        technique.maxVariants())
                .put("passes", passes));
    }

    public static ShaderGraphComputeTechnique read(
            String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root == null || !root.isObject()
                || !"compute-technique".equals(
                        root.requireString("asset"))
                || root.require("format").intValue()
                        != CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph compute-technique asset format");
        }
        JsonValue passes = root.require("passes");
        requireArray(passes, "compute passes");
        ShaderGraphComputeTechniquePass[] values =
                new ShaderGraphComputeTechniquePass[passes.size()];
        for (int i = 0; i < values.length; i++) {
            JsonValue pass = passes.require(i);
            JsonValue variants = pass.require("variants");
            requireArray(variants, "compute variants");
            ShaderGraphComputeVariant[] variantValues =
                    new ShaderGraphComputeVariant[
                            variants.size()];
            for (int j = 0; j < variantValues.length; j++) {
                variantValues[j] =
                        readVariant(variants.require(j));
            }
            values[i] = ShaderGraphComputeTechniquePass
                    .builder(ShaderPassId.of(
                            pass.requireString("id")))
                    .defaultVariant(pass.requireString(
                            "defaultVariant"))
                    .variants(variantValues)
                    .build();
        }
        return ShaderGraphComputeTechnique.builder(
                        root.requireString("id"))
                .maxVariants(
                        root.require("maxVariants").intValue())
                .passes(values)
                .build();
    }

    private static JsonValue variant(
            ShaderGraphComputeVariant variant) {
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
                        ShaderGraphComputeProgramCodec.write(
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

    private static ShaderGraphComputeVariant readVariant(
            JsonValue value) {
        JsonValue switches = value.require("staticValues");
        JsonValue profiles = value.require("profiles");
        JsonValue features = value.require("features");
        requireArray(switches, "compute static values");
        requireArray(profiles, "compute profiles");
        requireArray(features, "compute features");
        ShaderGraphStaticValue[] staticValues =
                new ShaderGraphStaticValue[switches.size()];
        for (int i = 0; i < staticValues.length; i++) {
            JsonValue item = switches.require(i);
            staticValues[i] = ShaderGraphStaticValue.bool(
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
                        "Unknown compute feature: "
                                + features.require(i).stringValue(),
                        exception);
            }
        }
        ShaderGraphComputeVariant.Builder builder =
                ShaderGraphComputeVariant.builder(
                                value.requireString("key"),
                                ShaderGraphComputeProgramCodec.read(
                                        JsonWriter.compact(
                                                value.require(
                                                        "program"))))
                        .staticValues(staticValues)
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
