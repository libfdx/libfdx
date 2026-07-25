package io.github.libfdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MemoryClipboardTest {
    @Test
    void preservesSupplementaryTextAndNormalizesNull() {
        MemoryClipboard clipboard = new MemoryClipboard();
        String emojiText = "Emoji " + new String(Character.toChars(0x1F600));

        clipboard.setText(emojiText);
        assertEquals(emojiText, clipboard.getText());

        clipboard.setText(null);
        assertEquals("", clipboard.getText());
    }
}
