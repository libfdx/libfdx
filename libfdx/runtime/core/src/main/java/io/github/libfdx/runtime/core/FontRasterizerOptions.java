package io.github.libfdx.runtime.core;

import java.util.LinkedHashSet;
import java.util.Set;

public final class FontRasterizerOptions {
    private final float size;
    private final String characters;
    private final int padding;
    private final int atlasWidth;

    public FontRasterizerOptions(float size, String characters, int padding, int atlasWidth) {
        this.size = size > 0.0f ? size : 16.0f;
        this.characters = characters != null && characters.length() > 0 ? unique(characters) : defaultCharacters();
        this.padding = Math.max(0, padding);
        this.atlasWidth = Math.max(64, atlasWidth);
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