package io.github.libfdx.graphics.g2d;

import java.util.LinkedHashSet;
import java.util.Set;

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

    public static FreeTypeFontOptions defaults(float size) {
        return new FreeTypeFontOptions("Dialog", size, defaultCharacters(), 2, 512);
    }

    public FreeTypeFontOptions family(String family) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    public FreeTypeFontOptions size(float size) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    public FreeTypeFontOptions characters(String characters) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    public FreeTypeFontOptions padding(int padding) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    public FreeTypeFontOptions atlasWidth(int atlasWidth) {
        return new FreeTypeFontOptions(family, size, characters, padding, atlasWidth);
    }

    public String family() {
        return family;
    }

    public float size() {
        return size;
    }

    public String characters() {
        return characters;
    }

    public int padding() {
        return padding;
    }

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
