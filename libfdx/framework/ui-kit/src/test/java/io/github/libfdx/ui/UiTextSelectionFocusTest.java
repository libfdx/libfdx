package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;

import org.junit.jupiter.api.Test;

/** Exercises selection lifetime through real focus and clipboard input routing. */
final class UiTextSelectionFocusTest {
    @Test
    void mouseAndTabKeepOnlyTheFocusedTextSelected() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiNode[] fields = new UiNode[3];
        UiState<String> first = Ui.state("First café");
        UiState<String> second = Ui.state("Second name");
        UiState<String> editable = Ui.state("Editable text");
        root.resize(320, 280);
        root.setContent(
                scope ->
                        scope.column(
                                Ui.modifier().gap(8),
                                column -> {
                                    fields[0] =
                                            column.textArea(
                                                    Ui.modifier().size(240, 44),
                                                    first,
                                                    UiTextAreaOptions.defaults().readOnly(true));
                                    fields[1] =
                                            column.textArea(
                                                    Ui.modifier().size(240, 44),
                                                    second,
                                                    UiTextAreaOptions.defaults().readOnly(true));
                                    fields[2] =
                                            column.textField(Ui.modifier().size(240, 44), editable);
                                }));
        root.update(0);
        try {
            click(input, fields[0]);
            shortcut(input, Key.A);
            UiTextFieldModel previous = model(fields[0]);
            int previousCursor = previous.cursor();
            assertEquals(first.get(), previous.selectedText());

            // Tab transfers focus without a pointer click that would reset the old model.
            input.dispatchKeyDown(Key.TAB);
            input.dispatchKeyUp(Key.TAB);
            assertTrue(fields[1].focused());
            assertFalse(previous.hasSelection());
            assertEquals(previousCursor, previous.cursor());
            shortcut(input, Key.A);
            shortcut(input, Key.C);
            assertEquals(second.get(), input.clipboard().getText());

            click(input, fields[2]);
            assertFalse(model(fields[1]).hasSelection());
            shortcut(input, Key.A);
            shortcut(input, Key.C);
            assertEquals(editable.get(), input.clipboard().getText());

            click(input, fields[0]);
            assertFalse(model(fields[2]).hasSelection());
            assertFalse(model(fields[0]).hasSelection(), "Old highlights must not reappear");
            assertEquals("First café", first.get());
            assertEquals("Second name", second.get());
            assertEquals("Editable text", editable.get());
        } finally {
            root.dispose();
        }
    }

    @Test
    void buttonAndBlankSpaceClearSelectionButRecompositionDoesNot() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiNode[] nodes = new UiNode[2];
        UiState<String> text = Ui.state("Copy this text");
        int[] clicks = {0};
        root.resize(320, 280);
        root.setContent(
                scope ->
                        scope.column(
                                Ui.modifier().gap(8),
                                column -> {
                                    nodes[0] =
                                            column.textArea(
                                                    Ui.modifier().size(240, 44),
                                                    text,
                                                    UiTextAreaOptions.defaults().readOnly(true));
                                    nodes[1] =
                                            column.button(
                                                    "Choose",
                                                    Ui.modifier().size(240, 44),
                                                    () -> clicks[0]++);
                                }));
        root.update(0);
        try {
            click(input, nodes[0]);
            shortcut(input, Key.A);
            root.requestCompose();
            root.update(0);
            assertTrue(model(nodes[0]).hasSelection(), "Same focused node keeps its selection");

            click(input, nodes[1]);
            assertEquals(1, clicks[0]);
            assertFalse(model(nodes[0]).hasSelection());

            click(input, nodes[0]);
            shortcut(input, Key.A);
            input.dispatchPointerDown(MouseButton.LEFT, 300, 260);
            input.dispatchPointerUp(MouseButton.LEFT, 300, 260);
            assertFalse(nodes[0].focused());
            assertFalse(model(nodes[0]).hasSelection());
            assertEquals("Copy this text", text.get());
        } finally {
            root.dispose();
        }
    }

    private static UiTextFieldModel model(UiNode node) {
        return (UiTextFieldModel) node.descriptor();
    }

    private static void click(DefaultInput input, UiNode node) {
        int x = (int) node.bounds().x() + 12;
        int y = (int) node.bounds().y() + 12;
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
    }

    private static void shortcut(DefaultInput input, Key key) {
        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
    }
}
