package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Texture;

/** PBR-only attributes understood by the built-in PBR renderer. */
public final class PbrAttributes {
    public static final MaterialAttributeType<FloatMaterialAttribute>
            NORMAL_SCALE = floatType("pbr.normalScale");
    public static final MaterialAttributeType<FloatMaterialAttribute>
            OCCLUSION_STRENGTH = floatType("pbr.occlusionStrength");

    /** Scales the tangent-space normal's X and Y components before normalization. */
    public static FloatMaterialAttribute normalScale(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Normal scale must be finite");
        return new FloatMaterialAttribute(NORMAL_SCALE, value);
    }
    /** Blends occlusion from one (strength zero) to the sampled value (strength one). */
    public static FloatMaterialAttribute occlusionStrength(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Occlusion strength must be in [0, 1]");
        return new FloatMaterialAttribute(OCCLUSION_STRENGTH, value);
    }
    public static float normalScale(Material material) { return value(material, NORMAL_SCALE, 1); }
    public static float occlusionStrength(Material material) { return value(material, OCCLUSION_STRENGTH, 1); }
    public static final MaterialAttributeType<FloatMaterialAttribute>
            METALLIC_FACTOR = floatType("pbr.metallicFactor");
    public static final MaterialAttributeType<FloatMaterialAttribute>
            ROUGHNESS_FACTOR = floatType("pbr.roughnessFactor");
    public static final MaterialAttributeType<TextureMaterialAttribute>
            METALLIC_ROUGHNESS_TEXTURE = textureType(
                    "pbr.metallicRoughnessTexture");
    public static final MaterialAttributeType<TextureMaterialAttribute>
            OCCLUSION_TEXTURE = textureType("pbr.occlusionTexture");

    private PbrAttributes() {
    }

    public static FloatMaterialAttribute metallicFactor(float value) {
        return new FloatMaterialAttribute(METALLIC_FACTOR, value);
    }

    public static FloatMaterialAttribute roughnessFactor(float value) {
        return new FloatMaterialAttribute(ROUGHNESS_FACTOR, value);
    }

    public static TextureMaterialAttribute metallicRoughnessTexture(
            Texture texture) {
        return new TextureMaterialAttribute(
                METALLIC_ROUGHNESS_TEXTURE, texture);
    }

    public static TextureMaterialAttribute occlusionTexture(Texture texture) {
        return new TextureMaterialAttribute(
                OCCLUSION_TEXTURE, texture);
    }

    public static float metallicFactor(Material material) {
        return value(material, METALLIC_FACTOR, 0.0f);
    }

    public static float roughnessFactor(Material material) {
        return value(material, ROUGHNESS_FACTOR, 1.0f);
    }

    public static Texture metallicRoughnessTexture(Material material) {
        return texture(material, METALLIC_ROUGHNESS_TEXTURE);
    }

    public static Texture occlusionTexture(Material material) {
        return texture(material, OCCLUSION_TEXTURE);
    }

    private static float value(Material material,
            MaterialAttributeType<FloatMaterialAttribute> type,
            float defaultValue) {
        FloatMaterialAttribute attribute = find(material, type);
        return attribute != null ? attribute.value() : defaultValue;
    }

    private static Texture texture(Material material,
            MaterialAttributeType<TextureMaterialAttribute> type) {
        TextureMaterialAttribute attribute = find(material, type);
        return attribute != null ? attribute.texture() : null;
    }

    private static <T extends MaterialAttribute> T find(Material material,
            MaterialAttributeType<T> type) {
        return material != null ? material.find(type) : null;
    }

    private static MaterialAttributeType<FloatMaterialAttribute> floatType(
            String id) {
        return new MaterialAttributeType<FloatMaterialAttribute>(
                id, FloatMaterialAttribute.class);
    }

    private static MaterialAttributeType<TextureMaterialAttribute> textureType(
            String id) {
        return new MaterialAttributeType<TextureMaterialAttribute>(
                id, TextureMaterialAttribute.class);
    }
}
