package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.BitmapFont;

public final class UiFont {
    private final UiFontKind kind;
    private final String family;
    private final String path;
    private final float size;
    private final BitmapFont bitmapFont;
    private final String characters;
    private final UiFont fallback;

    private UiFont(UiFontKind kind, String family, String path, float size, BitmapFont bitmapFont, String characters,
            UiFont fallback) {
        this.kind = kind != null ? kind : UiFontKind.FAMILY;
        this.family = family;
        this.path = path;
        this.size = size > 0.0f ? size : 16.0f;
        this.bitmapFont = bitmapFont;
        this.characters = characters;
        this.fallback = fallback;
    }

    public static UiFont family(String family, float size) {
        return new UiFont(UiFontKind.FAMILY, family, null, size, null, null, null);
    }

    public static UiFont file(String path, float size) {
        String value = path != null ? path.toLowerCase() : "";
        if (value.endsWith(".fnt")) {
            return bitmapFile(path, size);
        }
        return freeType(path, size);
    }

    public static UiFont bitmap(BitmapFont font) {
        return new UiFont(UiFontKind.BITMAP, null, null, font != null ? font.nativeSize() : 16.0f, font, null, null);
    }

    public static UiFont bitmapFile(String path, float size) {
        return new UiFont(UiFontKind.BITMAP_FILE, null, path, size, null, null, null);
    }

    public static UiFont freeType(String path, float size) {
        return new UiFont(UiFontKind.FREETYPE_FILE, null, path, size, null, null, null);
    }

    public UiFont characters(String characters) {
        return new UiFont(kind, family, path, size, bitmapFont, characters, fallback);
    }

    public UiFont fallback(UiFont fallback) {
        return new UiFont(kind, family, path, size, bitmapFont, characters, fallback);
    }

    public UiFontKind kind() {
        return kind;
    }

    public String family() {
        return family;
    }

    public String path() {
        return path;
    }

    public float size() {
        return size;
    }

    public BitmapFont bitmapFont() {
        return bitmapFont;
    }

    public String characters() {
        return characters;
    }

    public UiFont fallback() {
        return fallback;
    }
}
