package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.InputRouter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.ui.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PreviewInputTest {
    @Test
    void defaultPreviewShowsPointerFeedbackWithoutRunningActions() {
        try (Panel panel = new Panel(new UiPreviewConfig(320, 240))) {
            move(panel.input, panel.button, 0.5f);
            assertTrue(panel.button.hovered());
            press(panel.input, panel.button, 0.5f);
            assertTrue(panel.button.pressed());
            assertFalse(panel.button.focused());
            release(panel.input, panel.button, 0.5f);
            assertFalse(panel.button.pressed());
            key(panel.input, Key.TAB);
            key(panel.input, Key.ENTER);
            key(panel.input, Key.SPACE);
            click(panel.input, panel.checkbox);
            click(panel.input, panel.editor);
            panel.input.dispatchTextInput("edited");
            key(panel.input, Key.BACKSPACE);
            press(panel.input, panel.slider, 0.25f);
            move(panel.input, panel.slider, 0.9f);
            assertTrue(panel.slider.pressed());
            assertEquals(0, panel.value.get());
            release(panel.input, panel.slider, 0.9f);
            panel.root.update(0);
            assertEquals(0, panel.clicks);
            assertFalse(panel.checked.get());
            assertEquals("original", panel.text.get());
            assertEquals(0, panel.value.get());
            assertFalse(panel.button.focused());
            assertFalse(panel.editor.focused());
            // The host router remains available for preview commands even without UI input.
            key(panel.input, Key.F8);
            assertEquals(1, panel.hostCommands);
        }
    }

    @Test
    void explicitInteractivePreviewInvokesCallbacksAndSupportsEditing() {
        try (Panel panel = new Panel(new UiPreviewConfig(320, 240).interactive(true))) {
            assertSame(panel.input, panel.root.input());
            click(panel.input, panel.button);
            assertEquals(1, panel.clicks);
            key(panel.input, Key.ENTER);
            assertEquals(2, panel.clicks, "Keyboard activation must work only in opted-in mode");
            click(panel.input, panel.checkbox);
            assertTrue(panel.checked.get());
            click(panel.input, panel.editor);
            panel.input.dispatchTextInput("edited");
            assertTrue(panel.text.get().contains("edited"));
            click(panel.input, panel.slider);
            assertTrue(panel.value.get() > 0);
            key(panel.input, Key.F8);
            assertEquals(1, panel.hostCommands);
        }
    }

    @Test
    void optingBackOutRestoresTheDesignPreviewPolicy() {
        try (Panel panel = new Panel(new UiPreviewConfig(320, 240).interactive(true).interactive(false))) {
            move(panel.input, panel.button, 0.5f);
            press(panel.input, panel.button, 0.5f);
            assertFalse(panel.button.hovered());
            assertFalse(panel.button.pressed());
            release(panel.input, panel.button, 0.5f);
            assertEquals(0, panel.clicks);
            assertNull(panel.root.input());
        }
    }

    @Test
    void pickerClicksSelectByNameWithoutActivatingThePreviewedScreen() {
        DefaultInput input = new DefaultInput();
        InputRouter router = new InputRouter();
        input.addProcessor(router);
        UiToolkit toolkit = new UiToolkit(null);
        UiRoot controls = toolkit.root(null, null).allowFontFallback(true);
        UiRoot content = toolkit.root(null, null).allowFontFallback(true);
        List<String> targets = List.of("FirstScreen", "SecondScreen", "ThirdScreen");
        int[] selected = {0};
        int[] actions = {0};
        try (PreviewClassPicker picker = new PreviewClassPicker(controls, input, targets, 0,
                index -> selected[0] = index)) {
            router.add(picker);
            content.input(input, router).setContent(ui ->
                    ui.button("Previewed action", Ui.modifier().fill(), () -> actions[0]++));
            content.resize(500, 400);
            picker.resize(500, 400);
            picker.render(0);

            click(input, findText(controls.rootNode(), "Choose class"));
            picker.render(0);
            assertNotNull(findText(controls.rootNode(), "FirstScreen (current)"));
            input.dispatchPointerDown(MouseButton.LEFT, 4, 4);
            input.dispatchPointerUp(MouseButton.LEFT, 4, 4);
            key(input, Key.ESCAPE);
            picker.render(0);
            assertNull(findText(controls.rootNode(), "ThirdScreen"));
            assertEquals(0, selected[0]);

            click(input, findText(controls.rootNode(), "Choose class"));
            picker.render(0);
            click(input, findText(controls.rootNode(), "ThirdScreen"));
            picker.render(2);
            assertEquals(2, selected[0]);
            assertNull(findText(controls.rootNode(), "ThirdScreen (current)"));
            assertEquals(0, actions[0]);

            key(input, Key.TAB);
            key(input, Key.ENTER);
            assertEquals(1, actions[0], "The closed picker must leave the target's keyboard input available");
        } finally {
            content.dispose();
            input.removeProcessor(router);
        }
    }

    private static UiNode findText(UiNode node, String text) {
        if (text.equals(node.text())) return node;
        for (int i = 0; i < node.children().size(); i++) {
            UiNode match = findText(node.children().get(i), text);
            if (match != null) return match;
        }
        return null;
    }

    private static void click(DefaultInput input, UiNode node) {
        move(input, node, 0.75f);
        press(input, node, 0.75f);
        release(input, node, 0.75f);
    }

    private static void move(DefaultInput input, UiNode node, float fraction) {
        input.dispatchPointerMoved(x(node, fraction), y(node));
    }

    private static void press(DefaultInput input, UiNode node, float fraction) {
        input.dispatchPointerDown(MouseButton.LEFT, x(node, fraction), y(node));
    }

    private static void release(DefaultInput input, UiNode node, float fraction) {
        input.dispatchPointerUp(MouseButton.LEFT, x(node, fraction), y(node));
    }

    private static int x(UiNode node, float fraction) {
        return Math.round(node.bounds().x() + node.bounds().width() * fraction);
    }

    private static int y(UiNode node) {
        return Math.round(node.bounds().y() + node.bounds().height() * 0.5f);
    }

    private static void key(DefaultInput input, Key key) {
        input.dispatchKeyDown(key); input.dispatchKeyUp(key);
    }

    private static final class Panel implements AutoCloseable {
        final DefaultInput input = new DefaultInput();
        final InputRouter router = new InputRouter();
        final UiState<String> text = Ui.state("original");
        final UiBooleanState checked = Ui.state(false);
        final UiFloatState value = Ui.state(0f);
        final UiRoot root;
        UiNode button, checkbox, editor, slider;
        int clicks, hostCommands;
        Panel(UiPreviewConfig config) {
            router.add(new InputAdapter() {
                public boolean keyDown(KeyEvent event) {
                    if (event.key() != Key.F8) return false;
                    hostCommands++; return true;
                }

            });
            input.addProcessor(router);
            root = UiPreview.createRoot(config, null, null, null, input, router).allowFontFallback(true);
            root.resize(320, 240);
            root.setContent(ui -> ui.column(Ui.modifier().fill().gap(5), column -> {
                button = column.button("Action", Ui.modifier().size(180, 35), () -> clicks++);
                checkbox = column.checkbox("Checked", Ui.modifier().size(180, 35), checked);
                editor = column.textField(Ui.modifier().size(180, 35), text);
                slider = column.slider(Ui.modifier().size(180, 35), value, 0, 1);
            }));
            root.update(0);
        }

        public void close() { root.dispose(); input.removeProcessor(router); }

    }
}
