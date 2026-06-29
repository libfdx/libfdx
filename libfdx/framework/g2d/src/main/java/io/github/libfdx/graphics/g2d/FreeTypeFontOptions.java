package io.github.libfdx.graphics.g2d;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores option values for a free type font.
 *
 * @author xpenatan
 */
public final class FreeTypeFontOptions {
    private final String family;
    private final float size;
    private final String characters;
    private final int padding;
    private final int atlasWidth;

    private FreeTypeFontOptions(String family, float size, String characters, int padding, int atlasWidth) {
        this.family = family != null && family.length() > 0 ? family : "Dialog";
        this.size = size > 0.0f ? size : 16.0f;
        this.characters = characters != null && characters.length() > 0 ? unique(characters) : defaultCharacters();
        this.padding = Math.max(0, padding);
        this.atlasWidth = Math.max(64, atlasWidth);
    }

    /**
     * Creates a free type font options.
     *
     * @param size the size
     * @return a new free type font options
     */
    public static FreeTypeFontOptions defaults(float size) {
        return new FreeTypeFontOptions("Dialog", size, defaultCharacters(), 2, 512);
    }

    /**
     * Sets the family and returns this free type font options.
     *
     * @param family the family
     * @return this free type font options for chaining
     */
    public FreeTypeFontOptions family(String family) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    /**
     * Sets the size and returns this free type font options.
     *
     * @param size the size
     * @return this free type font options for chaining
     */
    public FreeTypeFontOptions size(float size) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    /**
     * Sets the characters and returns this free type font options.
     *
     * @param characters the characters
     * @return this free type font options for chaining
     */
    public FreeTypeFontOptions characters(String characters) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    /**
     * Sets the padding and returns this free type font options.
     *
     * @param padding the padding
     * @return this free type font options for chaining
     */
    public FreeTypeFontOptions padding(int padding) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    /**
     * Sets the atlas width and returns this free type font options.
     *
     * @param atlasWidth the atlas width
     * @return this free type font options for chaining
     */
    public FreeTypeFontOptions atlasWidth(int atlasWidth) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
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
        Set<Character> seen = new LinkedHashSet<Character>();
        for (int i = 0; i < characters.length(); i++) {
            seen.add(Character.valueOf(characters.charAt(i)));
        }
        StringBuilder builder = new StringBuilder();
        for (Character character : seen) {
            builder.append(character.charValue());
        }
        return builder.toString();
    }
}
