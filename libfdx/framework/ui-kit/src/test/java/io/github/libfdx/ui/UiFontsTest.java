package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

final class UiFontsTest {
    @Test
    void exposesBundledDefaultTrueTypeFont() throws Exception {
        UiFont font = UiFonts.defaultFont(18.0f);

        assertEquals(UiFontKind.FREETYPE_FILE, font.kind());
        assertEquals(UiFonts.DEFAULT_TTF_PATH, font.path());
        assertEquals(18.0f, font.size());
        try (InputStream input = UiFonts.class.getClassLoader()
                .getResourceAsStream(UiFonts.DEFAULT_TTF_PATH)) {
            assertNotNull(input);
            assertArrayEquals(new byte[] { 0, 1, 0, 0 }, input.readNBytes(4));
        }
    }

    @Test
    void shipsDefaultFontLicenseBesideFont() throws Exception {
        try (InputStream input = UiFonts.class.getClassLoader()
                .getResourceAsStream(UiFonts.DEFAULT_FONT_LICENSE_PATH)) {
            assertNotNull(input);
        }
    }
}
