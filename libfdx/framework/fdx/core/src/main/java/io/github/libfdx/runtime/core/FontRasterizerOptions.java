package io.github.libfdx.runtime.core;

import io.github.libfdx.collections.IntSet;

/**
 * Stores option values for a font rasterizer.
 *
 * @author xpenatan
 */
public final class FontRasterizerOptions {
    private final float size;
    private final String characters;
    private final int padding;
    private final int atlasWidth;

    /**
     * Creates a font rasterizer options.
     *
     * @param size the size
     * @param characters the characters
     * @param padding the padding
     * @param atlasWidth the atlas width
     */
    public FontRasterizerOptions(float size, String characters, int padding, int atlasWidth) {
        this.size = size > 0.0f ? size : 16.0f;
        this.characters = characters != null && characters.length() > 0 ? unique(characters) : defaultCharacters();
        this.padding = Math.max(0, padding);
        this.atlasWidth = Math.max(64, atlasWidth);
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
     * Returns the characters.
     *
     * @return the characters
     */
    public String characters() {
        return characters;
    }

    /**
     * Returns the padding.
     *
     * @return the padding
     */
    public int padding() {
        return padding;
    }

    /**
     * Returns the atlas width.
     *
     * @return the atlas width
     */
    public int atlasWidth() {
        return atlasWidth;
    }

    private static String defaultCharacters() {
        StringBuilder builder = new StringBuilder();
        for (char c = 32; c <= 126; c++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static String unique(String characters) {
        IntSet seen = new IntSet();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < characters.length();) {
            int codePoint = characters.codePointAt(i);
            if (seen.add(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }
}
