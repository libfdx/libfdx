package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class UiTextFieldModelTest {
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @Test
    void cursorMovementAndDeletionKeepSupplementaryCodePointsIntact() {
        UiState<String> state = Ui.state("A" + EMOJI + "B");
        UiTextFieldModel model = new UiTextFieldModel(state);

        model.cursor(2);
        assertEquals(1, model.cursor(), "A cursor inside a surrogate pair must move to its leading boundary");
        assertEquals(3, model.nextCursor());

        model.moveCursor(model.nextCursor(), false);
        model.backspace();

        assertEquals("AB", state.get());
        assertEquals(1, model.cursor());

        model.insert(EMOJI);
        assertEquals("A" + EMOJI + "B", state.get());
        assertEquals(3, model.cursor());

        model.delete();
        assertEquals("A" + EMOJI, state.get());
    }

    @Test
    void selectionsAreNormalizedToCodePointBoundaries() {
        UiState<String> state = Ui.state("A" + EMOJI + "B");
        UiTextFieldModel model = new UiTextFieldModel(state);

        model.select(2, 3);

        assertEquals(1, model.selectionMin());
        assertEquals(3, model.selectionMax());
        assertEquals(EMOJI, model.selectedText());

        model.deleteSelection();
        assertEquals("AB", state.get());
        assertFalse(model.hasSelection());
    }

    @Test
    void passwordMaskUsesOneCharacterPerCodePoint() {
        UiNode node = new UiNode(UiNodeType.TEXT_FIELD, "password");

        assertEquals("***", node.maskedText("A" + EMOJI + "B"));
    }
}
