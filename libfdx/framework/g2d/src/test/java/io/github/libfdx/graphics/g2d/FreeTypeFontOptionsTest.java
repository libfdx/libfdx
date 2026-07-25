package io.github.libfdx.graphics.g2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    public void characterNormalizationKeepsOneCompleteSupplementaryCodePoint() {
        String emoji = new String(Character.toChars(0x1F600));

        String characters = FreeTypeFontOptions.defaults(16.0f)
                .characters("A" + emoji + emoji + "A")
                .characters();

        assertEquals(2, characters.codePointCount(0, characters.length()));
        assertEquals("A" + emoji, characters);
    }

    @Test
    public void addingEmojiRetainsDefaultCharacters() {
        String emoji = new String(Character.toChars(0x1F600));

        String characters = FreeTypeFontOptions.defaults(16.0f)
                .addCharacters(emoji + emoji)
                .characters();

        assertTrue(characters.indexOf('A') >= 0);
        assertEquals(1, characters.codePoints().filter(codePoint -> codePoint == 0x1F600).count());
    }
}
