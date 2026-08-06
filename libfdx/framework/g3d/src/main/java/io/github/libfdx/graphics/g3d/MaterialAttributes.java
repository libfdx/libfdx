package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Texture;
import io.github.libfdx.math.Color;

/**
 * Shading-model-neutral attributes understood by the standard 3D shaders.
 *
 * <p>A shader may consume only the attributes relevant to it. In particular,
 * unlit materials use base color, texture, emissive, alpha, and optional fog
 * without requiring metallic or roughness values.</p>
 */
public final class MaterialAttributes {
    public static final MaterialAttributeType<ColorMaterialAttribute>
            BASE_COLOR = colorType("material.baseColor");
    public static final MaterialAttributeType<TextureMaterialAttribute>
            BASE_COLOR_TEXTURE = textureType("material.baseColorTexture");
    public static final MaterialAttributeType<TextureMaterialAttribute>
            NORMAL_TEXTURE = textureType("material.normalTexture");
    public static final MaterialAttributeType<ColorMaterialAttribute>
            EMISSIVE_COLOR = colorType("material.emissiveColor");
    public static final MaterialAttributeType<TextureMaterialAttribute>
            EMISSIVE_TEXTURE = textureType("material.emissiveTexture");
    public static final MaterialAttributeType<FloatMaterialAttribute>
            ALPHA_CUTOFF = floatType("material.alphaCutoff");
    public static final MaterialAttributeType<FloatMaterialAttribute>
            LIGHTING_INFLUENCE = floatType("material.lightingInfluence");

    private MaterialAttributes() {
    }

    public static ColorMaterialAttribute baseColor(Color value) {
        return new ColorMaterialAttribute(BASE_COLOR,
                value != null ? value : Color.WHITE);
    }

    public static ColorMaterialAttribute baseColor(
            float red, float green, float blue, float alpha) {
        return baseColor(new Color(red, green, blue, alpha));
    }

    public static TextureMaterialAttribute baseColorTexture(Texture texture) {
        return new TextureMaterialAttribute(BASE_COLOR_TEXTURE, texture);
    }

    public static TextureMaterialAttribute normalTexture(Texture texture) {
        return new TextureMaterialAttribute(NORMAL_TEXTURE, texture);
    }

    public static ColorMaterialAttribute emissiveColor(Color value) {
        return new ColorMaterialAttribute(EMISSIVE_COLOR,
                value != null ? value : Color.BLACK);
    }

    public static ColorMaterialAttribute emissiveColor(
            float red, float green, float blue) {
        return emissiveColor(new Color(red, green, blue, 1.0f));
    }

    public static TextureMaterialAttribute emissiveTexture(Texture texture) {
        return new TextureMaterialAttribute(EMISSIVE_TEXTURE, texture);
    }

    public static FloatMaterialAttribute alphaCutoff(float value) {
        return new FloatMaterialAttribute(ALPHA_CUTOFF, value);
    }

    /**
     * Sets how much computed lighting contributes to a lit shading model.
     * Zero produces full-bright base color, one produces fully lit output.
     */
    public static FloatMaterialAttribute lightingInfluence(float value) {
        return new FloatMaterialAttribute(LIGHTING_INFLUENCE,
                clamp01(value));
    }

    public static Color baseColor(Material material) {
        ColorMaterialAttribute attribute = find(material, BASE_COLOR);
        return attribute != null ? attribute.value() : Color.WHITE;
    }

    public static Texture baseColorTexture(Material material) {
        return texture(material, BASE_COLOR_TEXTURE);
    }

    public static Texture normalTexture(Material material) {
        return texture(material, NORMAL_TEXTURE);
    }

    public static Color emissiveColor(Material material) {
        ColorMaterialAttribute attribute = find(material, EMISSIVE_COLOR);
        return attribute != null ? attribute.value() : Color.BLACK;
    }

    public static Texture emissiveTexture(Material material) {
        return texture(material, EMISSIVE_TEXTURE);
    }

    public static float alphaCutoff(Material material) {
        return value(material, ALPHA_CUTOFF, 0.5f);
    }

    public static float lightingInfluence(Material material) {
        return clamp01(value(material, LIGHTING_INFLUENCE, 1.0f));
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

    private static MaterialAttributeType<ColorMaterialAttribute> colorType(
            String id) {
        return new MaterialAttributeType<ColorMaterialAttribute>(
                id, ColorMaterialAttribute.class);
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

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
