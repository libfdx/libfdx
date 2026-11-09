package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import org.junit.jupiter.api.Test;

final class UiFocusEligibilityTest {
    @Test
    void disablingFocusedButtonsAndEditorsStopsInputAndClearsSelection() {
        DefaultInput input = new DefaultInput();
        UiRoot root = root(input);
        UiBooleanState enabled = Ui.state(true);
        UiState<String> text = Ui.state("value");
        UiNode[] nodes = new UiNode[2];
        int[] clicks = {0};
        root.setContent(ui -> ui.column(column -> {
            nodes[0] = column.button("Action", Ui.modifier().enabled(enabled.get()), () -> clicks[0]++);
            nodes[1] = column.textField(Ui.modifier().enabled(enabled.get()), text);
        }));
        try {
            root.update(0);
            key(input, Key.TAB);
            assertTrue(nodes[0].focused());
            enabled.set(false);
            root.update(0);
            key(input, Key.ENTER);
            assertEquals(0, clicks[0]);
            assertFalse(nodes[0].focused());
            enabled.set(true);
            root.update(0);
            key(input, Key.TAB);
            key(input, Key.TAB);
            assertTrue(nodes[1].focused());
            ((UiTextFieldModel)nodes[1].descriptor()).selectAll();
            enabled.set(false);
            root.update(0);
            input.dispatchTextInput("X");
            key(input, Key.BACKSPACE);
            assertEquals("value", text.get());
            assertFalse(nodes[1].focused());
            assertFalse(((UiTextFieldModel)nodes[1].descriptor()).hasSelection());
        } finally { root.dispose(); }
    }

    @Test
    void hiddenAndDisabledAncestorsExcludeTheirChildrenFromInput() {
        DefaultInput input = new DefaultInput();
        UiRoot root = root(input);
        UiBooleanState enabled = Ui.state(true);
        UiBooleanState visible = Ui.state(true);
        UiNode[] child = new UiNode[1];
        int[] clicks = {0};
        root.setContent(ui -> ui.animatedVisibility(visible.get(), Ui.animation().durationMillis(1), shown ->
                shown.panel(Ui.modifier().enabled(enabled.get()), panel ->
                        child[0] = panel.button("Action", () -> clicks[0]++))));
        try {
            root.update(0);
            key(input, Key.TAB);
            enabled.set(false);
            root.update(0);
            key(input, Key.TAB);
            key(input, Key.ENTER);
            assertEquals(0, clicks[0]);
            assertFalse(child[0].focused());
            enabled.set(true);
            root.update(0);
            key(input, Key.TAB);
            assertTrue(child[0].focused());
            visible.set(false);
            root.update(1);
            key(input, Key.ENTER);
            assertEquals(0, clicks[0]);
        } finally { root.dispose(); }
    }

    @Test
    void disablingAnAncestorCancelsCustomSurfaceCapture() {
        DefaultInput input = new DefaultInput();
        UiRoot root = root(input);
        UiBooleanState enabled = Ui.state(true);
        int[] cancels = {0};
        int[] keys = {0};
        UiNode[] node = new UiNode[1];
        UiSurfaceInput surface = new UiSurfaceInput() {
            @Override public UiPointerResult pointer(UiPointerEvent event) {
                if (event.phase() == UiPointerPhase.CANCEL) cancels[0]++;
                return UiPointerResult.CAPTURE;
            }
            @Override public boolean keyDown(KeyEvent event) { keys[0]++; return true; }
        };
        root.setContent(ui -> ui.panel(Ui.modifier().enabled(enabled.get()), panel ->
                node[0] = panel.custom("surface", Ui.modifier().size(100, 60).focusable(true),
                        custom -> custom.input(surface))));
        try {
            root.update(0);
            int x = Math.round(node[0].bounds().x() + 10);
            int y = Math.round(node[0].bounds().y() + 10);
            assertTrue(input.dispatchPointerDown(io.github.libfdx.input.MouseButton.LEFT, x, y));
            enabled.set(false);
            root.update(0);
            key(input, Key.A);
            input.dispatchPointerUp(io.github.libfdx.input.MouseButton.LEFT, x, y);
            assertEquals(1, cancels[0]);
            assertEquals(0, keys[0]);
        } finally { root.dispose(); }
    }

    @Test
    void nestedModalsContainKeyboardInputAndRestoreEligibleFocus() {
        DefaultInput input = new DefaultInput();
        UiRoot root = root(input);
        UiIntState level = Ui.state(0);
        UiNode[] nodes = new UiNode[4];
        int[] clicks = new int[4];
        int[] gameplay = {0};
        var router = new io.github.libfdx.input.InputRouter();
        input.addProcessor(router);
        root.input(input, router);
        router.add(new InputAdapter() {
            @Override public boolean keyDown(KeyEvent event) { gameplay[0]++; return false; }
        });
        root.setContent(ui -> {
            nodes[0] = ui.button("Background", () -> clicks[0]++);
            if (level.get() >= 1) ui.modal(Ui.modal("outer"), modal -> {
                nodes[1] = modal.button("First", () -> clicks[1]++);
                nodes[2] = modal.button("Second", () -> clicks[2]++);
                if (level.get() >= 2) modal.modal(Ui.modal("inner"), inner ->
                        nodes[3] = inner.button("Inner", () -> clicks[3]++));
            });
        });
        try {
            root.update(0);
            key(input, Key.TAB);
            level.set(1);
            root.update(0);
            assertTrue(nodes[1].focused());
            key(input, Key.ENTER);
            assertEquals(0, clicks[0]);
            assertEquals(1, clicks[1]);
            key(input, Key.TAB);
            assertTrue(nodes[2].focused());
            key(input, Key.TAB);
            assertTrue(nodes[1].focused());
            key(input, Key.A);
            assertEquals(0, gameplay[0]);
            level.set(2);
            root.update(0);
            assertTrue(nodes[3].focused());
            key(input, Key.TAB);
            assertTrue(nodes[3].focused());
            level.set(1);
            root.update(0);
            assertTrue(nodes[1].focused());
            level.set(0);
            root.update(0);
            assertTrue(nodes[0].focused());
            key(input, Key.ENTER);
            assertEquals(1, clicks[0]);
        } finally { root.dispose(); }
    }

    @Test
    void emptyModalsConsumeKeysAndDoNotRestoreRemovedBackgroundControls() {
        DefaultInput input = new DefaultInput();
        UiRoot root = root(input);
        UiBooleanState shown = Ui.state(false);
        UiNode[] background = new UiNode[1];
        root.setContent(ui -> {
            if (!shown.get()) background[0] = ui.button("Background", () -> {});
            else ui.modal(Ui.modal("empty"), null);
        });
        try {
            root.update(0);
            key(input, Key.TAB);
            UiNode removed = background[0];
            shown.set(true);
            root.update(0);
            assertTrue(root.handleKeyDown(new KeyEvent(0, Key.ENTER, false)));
            assertTrue(root.handleKeyDown(new KeyEvent(0, Key.TAB, false)));
            assertTrue(removed.isDisposed());
            shown.set(false);
            root.update(0);
            assertFalse(background[0].focused());
            key(input, Key.TAB);
            assertTrue(background[0].focused());
        } finally { root.dispose(); }
    }

    private static UiRoot root(DefaultInput input) {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true).input(input);
        root.resize(320, 240);
        return root;
    }

    private static void key(DefaultInput input, Key key) {
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
    }
}
