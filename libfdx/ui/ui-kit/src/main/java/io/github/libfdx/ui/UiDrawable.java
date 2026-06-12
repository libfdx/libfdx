package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.TextureRegion;

/**
 * Represents an ui drawable.
 *
 * @author xpenatan
 */
public final class UiDrawable {
    private static final UiDrawable NONE = new UiDrawable(UiDrawableType.NONE, null, null, null, null);

    private final UiDrawableType type;
    private final UiColor color;
    private final TextureRegion region;
    private final UiNinePatch ninePatch;
    private final String assetPath;

    private UiDrawable(UiDrawableType type, UiColor color, TextureRegion region, UiNinePatch ninePatch,
            String assetPath) {
        this.type = type != null ? type : UiDrawableType.NONE;
        this.color = color;
        this.region = region;
        this.ninePatch = ninePatch;
        this.assetPath = assetPath;
    }

    /**
     * Creates an UI drawable.
     *
     * @return a new UI drawable
     */
    public static UiDrawable none() {
        return NONE;
    }

    /**
     * Creates an UI drawable.
     *
     * @param color the color
     * @return a new UI drawable
     */
    public static UiDrawable color(UiColor color) {
        return new UiDrawable(UiDrawableType.COLOR, color, null, null, null);
    }

    /**
     * Creates an UI drawable.
     *
     * @param region the region
     * @return a new UI drawable
     */
    public static UiDrawable texture(TextureRegion region) {
        return new UiDrawable(UiDrawableType.TEXTURE, null, region, null, null);
    }

    /**
     * Creates an UI drawable.
     *
     * @param assetPath the asset path
     * @return a new UI drawable
     */
    public static UiDrawable ninePatch(String assetPath) {
        return new UiDrawable(UiDrawableType.NINE_PATCH, null, null, UiNinePatch.asset(assetPath), assetPath);
    }

    /**
     * Creates an UI drawable.
     *
     * @param ninePatch the nine patch
     * @return a new UI drawable
     */
    public static UiDrawable ninePatch(UiNinePatch ninePatch) {
        return new UiDrawable(UiDrawableType.NINE_PATCH, null, null, ninePatch, null);
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public UiDrawableType type() {
        return type;
    }

    /**
     * Returns the color.
     *
     * @return the color
     */
    public UiColor color() {
        return color;
    }

    /**
     * Returns the region.
     *
     * @return the region
     */
    public TextureRegion region() {
        return region;
    }

    /**
     * Returns the nine patch.
     *
     * @return the nine patch
     */
    public UiNinePatch ninePatch() {
        return ninePatch;
    }

    /**
     * Returns the asset path.
     *
     * @return the asset path
     */
    public String assetPath() {
        return assetPath;
    }
}
