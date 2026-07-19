package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class UiTextEngineTest {
    @Test
    public void reusesKeysWhenFontLookupsAlternate() {
        UiTextEngine engine = new UiTextEngine(null, null);
        UiFont regular = UiFont.freeType("fonts/regular.ttf", 16.0f);
        UiFont title = UiFont.freeType("fonts/title.ttf", 24.0f);

        String regularKey = engine.key(regular, 1.0f);
        String titleKey = engine.key(title, 1.0f);

        assertSame(regularKey, engine.key(regular, 1.0f));
        assertSame(titleKey, engine.key(title, 1.0f));
        assertNotSame(regularKey, engine.key(regular, 1.25f));
    }

    @Test
    public void boundsKeysForShortLivedFontObjects() {
        UiTextEngine engine = new UiTextEngine(null, null);
        UiFont firstFont = UiFont.freeType("fonts/first.ttf", 16.0f);
        String firstKey = engine.key(firstFont, 1.0f);

        for (int i = 0; i < 512; i++) {
            engine.key(UiFont.freeType("fonts/transient-" + i + ".ttf", 16.0f), 1.0f);
        }

        assertNotSame(firstKey, engine.key(firstFont, 1.0f));
    }
}
