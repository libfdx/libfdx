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
    void longSingleLineTracksTheCaretAndHitTestsTheScrolledText() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiState<String> value = Ui.state("");
        UiNode[] fields = new UiNode[1];
        root.resize(180, 80);
        root.setContent(ui -> fields[0] = ui.textField(Ui.modifier().size(120, 30), value));
        root.update(0);
        try {
            UiNode field = fields[0];
            click(input, field);
            String text = "abcdef".repeat(12);
            input.dispatchTextInput(text);
            root.update(0);
            UiRect end = root.textCaretBounds(field, text.length());
            assertTrue(end.right() <= field.bounds().right());
            assertTrue(root.textCaretBounds(field, 0).x() < field.bounds().x());
            UiRect previous = root.textCaretBounds(field, text.length() - 1);
            int x = root.displayX(previous.x()), y = root.displayY(previous.y() + 3);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            assertEquals(text.length() - 1, model(field).cursor());
            input.dispatchKeyDown(Key.HOME);
            input.dispatchKeyUp(Key.HOME);
            assertTrue(root.textCaretBounds(field, 0).x() >= field.bounds().x());
        } finally {
            root.dispose();
        }
    }

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

    @Test
    void selectableLabelsKeepTheirSourceAndSelectionThroughRecomposition() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiState<String> text = Ui.state("First line\nSecond line");
        UiNode[] label = new UiNode[1];
        try {
            root.resize(300, 200);
            root.setContent(ui -> label[0] = ui.selectableText(text.get()));
            root.update(0);
            click(input, label[0]);
            shortcut(input, Key.A);
            root.requestCompose();
            root.update(0);
            shortcut(input, Key.C);
            assertEquals(text.get(), input.clipboard().getText());
            input.dispatchTextInput("replacement");
            assertEquals(text.get(), model(label[0]).value());

            text.set("A\nB");
            root.update(0);
            shortcut(input, Key.C);
            assertEquals("A\nB", input.clipboard().getText());
        } finally {
            root.dispose();
        }
    }

    @Test
    void selectingPartlyVisibleOutputKeepsTheViewportUntilDraggedOutside() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiNode[] area = new UiNode[1];
        String source = "0123456789abcdef".repeat(4) + "\nshort line".repeat(24);
        UiState<String> value = Ui.state(source);
        try {
            root.resize(300, 200);
            root.setContent(ui -> area[0] = ui.textArea(Ui.modifier().size(150, 80), value,
                    UiTextAreaOptions.defaults().readOnly(true)));
            root.update(0);
            UiScrollState scroll = area[0].scrollState();
            scroll.scrollTo(3, 7);
            var start = root.textCaretBounds(area[0], 1);
            int x = root.displayX(start.x()), y = root.displayY(start.y() + 10);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            root.update(0);
            assertEquals(7f, scroll.y());
            input.dispatchPointerMoved(x + 32, y);
            input.dispatchPointerUp(MouseButton.LEFT, x + 32, y);
            shortcut(input, Key.C);
            assertFalse(input.clipboard().getText().isEmpty());
            assertEquals(3f, scroll.x());
            assertEquals(7f, scroll.y());

            shortcut(input, Key.A);
            assertEquals(7f, scroll.y());
            scroll.scrollTo(3, 24);
            shortcut(input, Key.C);
            assertEquals(source, input.clipboard().getText());
            assertEquals(24f, scroll.y());

            input.dispatchPointerDown(MouseButton.LEFT, x, y + 25);
            int below = root.displayY(area[0].bounds().bottom() + 10);
            input.dispatchPointerMoved(x, below);
            float afterDrag = scroll.y();
            assertTrue(afterDrag > 24);
            assertEquals(3f, scroll.x());
            input.dispatchPointerUp(MouseButton.LEFT, x, below);
            assertEquals(afterDrag, scroll.y());

            input.dispatchKeyDown(Key.END);
            input.dispatchKeyUp(Key.END);
            assertEquals(scroll.maxY(), scroll.y());
        } finally {
            root.dispose();
        }
    }

    @Test
    void selectingAllOfAReadOnlyLabelKeepsTheTextOrigin() {
        DefaultInput input = new DefaultInput();
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        UiNode[] label = new UiNode[1];
        try {
            root.resize(400, 120);
            root.setContent(ui -> label[0] = ui.selectableText("Canonical WGSL", Ui.modifier().width(60)));
            root.update(0);
            click(input, label[0]);
            float origin = root.textCaretBounds(label[0], 0).x();
            shortcut(input, Key.A);
            root.requestCompose();
            root.update(0);
            shortcut(input, Key.C);
            assertEquals("Canonical WGSL", input.clipboard().getText());
            assertEquals(origin, root.textCaretBounds(label[0], 0).x());
        } finally {
            root.dispose();
        }
    }

    @Test
    void touchDoubleTapSelectsAWordAndTheThirdTapCanSelectTheWholeOutput() {
        var input = new DefaultInput();
        var root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        var text = Ui.state("First word\nSecond line");
        var options = UiTextAreaOptions.defaults().readOnly(true);
        UiNode[] areas = new UiNode[2];
        try {
            root.resize(400, 300);
            root.setContent(ui -> ui.column(Ui.modifier().fill(), column -> {
                areas[0] = column.textArea(text, options);
                areas[1] = column.textArea(text, options.selectAllOnTripleClick(true));
            }));
            root.update(0);
            for (UiNode area : areas) {
                var caret = root.textCaretBounds(area, 2);
                int x = root.displayX(caret.x() + 1);
                int y = root.displayY(caret.y() + caret.height() / 2);
                for (int i = 0; i < 2; i++) {
                    input.dispatchTouchDown(0, x, y, 1f);
                    input.dispatchTouchUp(0, x, y, 1f);
                }
                assertEquals("First", model(area).selectedText());
                input.dispatchTouchDown(0, x, y, 1f);
                input.dispatchTouchUp(0, x, y, 1f);
                assertEquals(area == areas[0] ? "" : text.get(), model(area).selectedText());
                // The next tap after select-all starts a fresh gesture.
                if (area == areas[1]) {
                    input.dispatchTouchDown(0, x, y, 1f);
                    input.dispatchTouchUp(0, x, y, 1f);
                    assertFalse(model(area).hasSelection());
                }
            }
            assertFalse(model(areas[0]).hasSelection());
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
