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
    void treeDisclosureKeepsSelectionAndCanvasInputSeparate() {
        DefaultInput hierarchyInput = new DefaultInput();
        DefaultInput canvasInput = new DefaultInput();
        UiToolkit toolkit = new UiToolkit(null);
        UiRoot controls = toolkit.root(null, null).allowFontFallback(true);
        UiRoot content = toolkit.root(null, null).allowFontFallback(true);
        UiPreviewSetup setup = context -> ui -> { };
        List<PreviewHierarchy.Item> items = PreviewHierarchy.targets(List.of(
                new UiPreviewTarget("parent", "Parent", setup, List.of(
                        new UiPreviewTarget("group", "Group", setup, List.of(
                                new UiPreviewTarget("child", "Child", setup))))),
                new UiPreviewTarget("sibling", "Sibling", setup)));
        int[] selected = {0};
        int[] actions = {0};
        var debugLines = Ui.state(false);
        try {
            PreviewHierarchyView hierarchy = new PreviewHierarchyView(items, 0, index -> selected[0] = index, debugLines,
                    new PreviewRefreshStatus(null));
            controls.input(hierarchyInput).setContent(hierarchy);
            controls.resize(300, 400);
            controls.update(0.3f);
            content.input(canvasInput).setContent(ui ->
                    ui.button("Previewed action", Ui.modifier().fill(), () -> actions[0]++));
            content.resize(500, 400);
            content.viewport(new UiRect(220, 0, 280, 400));
            content.update(0);
            UiNode sibling = findLabel(controls.rootNode(), "Sibling");
            float siblingY = sibling.bounds().y();
            assertNull(findLabel(controls.rootNode(), "Child"));
            click(hierarchyInput, findLabel(controls.rootNode(), "Collapse Parent"));
            controls.update(0);
            controls.update(0.3f);
            assertNull(findLabel(controls.rootNode(), "Group"));
            assertSame(sibling, findLabel(controls.rootNode(), "Sibling"));
            assertTrue(sibling.bounds().y() < siblingY);
            assertEquals(0, selected[0]);
            key(hierarchyInput, Key.SPACE);
            controls.update(0.3f);
            click(hierarchyInput, findLabel(controls.rootNode(), "Expand Group"));
            controls.update(0.3f);
            UiNode parent = findLabel(controls.rootNode(), "Parent");
            UiNode child = findLabel(controls.rootNode(), "Child");
            assertTrue(child.bounds().x() > parent.bounds().x());
            click(hierarchyInput, child);
            assertEquals(2, selected[0]);
            hierarchy.active(2);
            controls.update(0);
            click(hierarchyInput, findLabel(controls.rootNode(), "Collapse Group"));
            controls.update(0.3f);
            assertNull(findLabel(controls.rootNode(), "Child"));
            assertEquals(2, selected[0]);
            assertEquals(3, hierarchy.step(2, false));
            assertEquals(0, hierarchy.step(2, true));
            click(hierarchyInput, findLabel(controls.rootNode(), "Group"));
            controls.update(0);
            assertEquals(1, selected[0]);
            assertNull(findLabel(controls.rootNode(), "Child"));
            assertEquals(0, actions[0]);
            UiNode action = findLabel(content.rootNode(), "Previewed action");
            assertEquals(280.0f, action.bounds().width());
            canvasInput.dispatchPointerDown(MouseButton.LEFT, 100, 100);
            canvasInput.dispatchPointerUp(MouseButton.LEFT, 100, 100);
            assertEquals(0, actions[0]);
            canvasInput.dispatchPointerDown(MouseButton.LEFT, content.displayX(20), content.displayY(100));
            canvasInput.dispatchPointerUp(MouseButton.LEFT, content.displayX(20), content.displayY(100));
            assertEquals(1, actions[0]);
            click(hierarchyInput, findLabel(controls.rootNode(), "Debug lines"));
            assertTrue(debugLines.get());
            assertEquals(1, selected[0]);
            assertEquals(1, actions[0]);
        } finally {
            content.dispose();
            controls.dispose();
        }
    }

    private static UiNode findLabel(UiNode node, String label) {
        if (label.equals(node.text()) || label.equals(node.modifier().semanticLabel())) return node;
        for (int i = 0; i < node.children().size(); i++) {
            UiNode match = findLabel(node.children().get(i), label);
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
