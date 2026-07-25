package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.FreeTypeFontOptions;

/**
 * Represents an ui font.
 *
 * @author xpenatan
 */
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

    /**
     * Creates an UI font.
     *
     * @param family the family
     * @param size the size
     * @return a new UI font
     */
    public static UiFont family(String family, float size) {
        return new UiFont(UiFontKind.FAMILY, family, null, size, null, null, null);
    }

    /**
     * Creates an UI font.
     *
     * @param path the asset or file path
     * @param size the size
     * @return a new UI font
     */
    public static UiFont file(String path, float size) {
        String value = path != null ? path.toLowerCase() : "";
        if (value.endsWith(".fnt")) {
            return bitmapFile(path, size);
        }
        return freeType(path, size);
    }

    /**
     * Creates an UI font.
     *
     * @param font the font
     * @return a new UI font
     */
    public static UiFont bitmap(BitmapFont font) {
        return new UiFont(UiFontKind.BITMAP, null, null, font != null ? font.nativeSize() : 16.0f, font, null, null);
    }

    /**
     * Creates an UI font.
     *
     * @param path the asset or file path
     * @param size the size
     * @return a new UI font
     */
    public static UiFont bitmapFile(String path, float size) {
        return new UiFont(UiFontKind.BITMAP_FILE, null, path, size, null, null, null);
    }

    /**
     * Creates an UI font.
     *
     * @param path the asset or file path
     * @param size the size
     * @return a new UI font
     */
    public static UiFont freeType(String path, float size) {
        return new UiFont(UiFontKind.FREETYPE_FILE, null, path, size, null, null, null);
    }

    /**
     * Sets the characters and returns this UI font.
     *
     * @param characters the characters
     * @return this UI font for chaining
     */
    public UiFont characters(String characters) {
        return new UiFont(kind, family, path, size, bitmapFont, characters, fallback);
    }

    /**
     * Adds characters to this font's glyph set and returns this UI font.
     *
     * <p>When no explicit set exists, the standard FreeType character set is retained.
     * This makes it convenient to opt into a finite set of symbols or emoji without
     * losing ordinary localized text.</p>
     *
     * @param characters the characters to add
     * @return this UI font for chaining
     */
    public UiFont addCharacters(String characters) {
        String base = this.characters != null
                ? this.characters
                : FreeTypeFontOptions.defaults(size).characters();
        String additional = characters != null ? characters : "";
        return new UiFont(kind, family, path, size, bitmapFont, base + additional, fallback);
    }

    /**
     * Sets the fallback and returns this UI font.
     *
     * @param fallback the fallback
     * @return this UI font for chaining
     */
    public UiFont fallback(UiFont fallback) {
        return new UiFont(kind, family, path, size, bitmapFont, characters, fallback);
    }

    /**
     * Returns the kind.
     *
     * @return the kind
     */
    public UiFontKind kind() {
        return kind;
    }

    /**
     * Returns the family.
     *
     * @return the family
     */
    public String family() {
        return family;
    }

    /**
     * Returns the path.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public float size() {
        return size;
    }

    /**
     * Returns the bitmap font.
     *
     * @return the bitmap font
     */
    public BitmapFont bitmapFont() {
        return bitmapFont;
    }

    /**
     * Returns the characters.
     *
     * @return the characters
     */
    public String characters() {
        return characters;
    }

    /**
     * Returns the fallback.
     *
     * @return this UI font for chaining
     */
    public UiFont fallback() {
        return fallback;
    }
}
