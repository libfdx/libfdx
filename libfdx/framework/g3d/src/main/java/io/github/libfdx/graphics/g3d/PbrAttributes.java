package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Texture;

/** PBR-only attributes understood by the built-in PBR renderer. */
public final class PbrAttributes {
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
