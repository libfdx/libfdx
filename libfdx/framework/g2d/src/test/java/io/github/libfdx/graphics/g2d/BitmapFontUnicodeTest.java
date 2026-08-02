package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.IntMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class BitmapFontUnicodeTest {
    private static final int EMOJI = 0x1F600;
    private static final String EMOJI_TEXT = new String(Character.toChars(EMOJI));

    @Test
    void measuresWrapsAndTruncatesSupplementaryCodePointsAsSingleGlyphs() {
        BitmapFont font = font();

        assertEquals(10.0f, font.width(EMOJI_TEXT, 10.0f));
        assertEquals(30.0f, font.width("A" + EMOJI_TEXT + "B", 10.0f));

        BitmapFontLayout wrapped = font.layout(EMOJI_TEXT + EMOJI_TEXT, 10.0f, 10.0f, true, false);
        assertArrayEquals(new String[] { EMOJI_TEXT, EMOJI_TEXT }, wrapped.lines().toArray(new String[0]));

        BitmapFontLayout truncated = font.layout(EMOJI_TEXT + "A", 10.0f, 30.0f, false, true);
        assertEquals(1, truncated.lines().size());
        assertFalse(hasUnpairedSurrogate(truncated.lines().get(0)));
    }

    private static BitmapFont font() {
        IntMap<BitmapFontGlyph> glyphs = new IntMap<BitmapFontGlyph>();
        glyphs.put('?', new BitmapFontGlyph('?', null, 0.0f, 0.0f, 10.0f));
        glyphs.put('.', new BitmapFontGlyph('.', null, 0.0f, 0.0f, 5.0f));
        glyphs.put('A', new BitmapFontGlyph('A', null, 0.0f, 0.0f, 10.0f));
        glyphs.put('B', new BitmapFontGlyph('B', null, 0.0f, 0.0f, 10.0f));
        glyphs.put(EMOJI, new BitmapFontGlyph(EMOJI, null, 0.0f, 0.0f, 10.0f));
        return new BitmapFont("unicode-test", 10.0f, 10.0f, 8.0f, glyphs, null, null, false);
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }
}
