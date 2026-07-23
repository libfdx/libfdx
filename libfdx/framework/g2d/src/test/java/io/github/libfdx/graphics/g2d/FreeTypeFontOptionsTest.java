package io.github.libfdx.graphics.g2d;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class FreeTypeFontOptionsTest {
    @Test
    public void defaultsIncludeLocalizedLatinGlyphsAndCommonPunctuation() {
        String characters = FreeTypeFontOptions.defaults(16.0f).characters();

        "você está ótimo — não, ação".codePoints().forEach(codePoint ->
                assertTrue(Character.isWhitespace(codePoint) || characters.indexOf(codePoint) >= 0,
                        "Missing default FreeType code point U+" + Integer.toHexString(codePoint)));
    }
}
