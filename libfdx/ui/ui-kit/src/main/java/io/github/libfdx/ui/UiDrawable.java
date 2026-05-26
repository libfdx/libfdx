package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.TextureRegion;

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

    public static UiDrawable none() {
        return NONE;
    }

    public static UiDrawable color(UiColor color) {
        return new UiDrawable(UiDrawableType.COLOR, color, null, null, null);
    }

    public static UiDrawable texture(TextureRegion region) {
        return new UiDrawable(UiDrawableType.TEXTURE, null, region, null, null);
    }

    public static UiDrawable ninePatch(String assetPath) {
        return new UiDrawable(UiDrawableType.NINE_PATCH, null, null, UiNinePatch.asset(assetPath), assetPath);
    }

    public static UiDrawable ninePatch(UiNinePatch ninePatch) {
        return new UiDrawable(UiDrawableType.NINE_PATCH, null, null, ninePatch, null);
    }

    public UiDrawableType type() {
        return type;
    }

    public UiColor color() {
        return color;
    }

    public TextureRegion region() {
        return region;
    }

    public UiNinePatch ninePatch() {
        return ninePatch;
    }

    public String assetPath() {
        return assetPath;
    }
}
