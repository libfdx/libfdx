package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.TextInputEvent;
import org.junit.jupiter.api.Test;

final class UiPreviewFeedbackTest {
    @Test
    void sliderDragMovesOnlyTheRenderedThumbUntilReleaseOrDetach() {
        DefaultInput input = new DefaultInput();
        UiRoot root = preview(input);
        UiFloatState volume = Ui.state(0.25f);
        UiBooleanState enabled = Ui.state(true);
        UiNode[] slider = new UiNode[1];
        int[] compositions = {0};
        root.setContent(ui -> {
            compositions[0]++;
            slider[0] = ui.slider(Ui.modifier().size(160, 40).enabled(enabled.get()), volume, 0, 1);
        });
        try {
            root.update(0);
            int initialCompositions = compositions[0];
            UiNode node = slider[0];
            int x = Math.round(node.bounds().x() + 20);
            int y = Math.round(node.bounds().y() + 20);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            assertTrue(node.pressed());
            assertTrue(root.sliderDisplayValue(node) < 0.25f);
            input.dispatchPointerMoved(x + 120, y + 90);
            assertTrue(node.pressed());
            assertTrue(root.sliderDisplayValue(node) > 0.75f);
            root.update(0);
            assertEquals(0.25f, volume.get());
            assertEquals(0.25f, node.floatValue());
            assertEquals(initialCompositions, compositions[0]);

            input.dispatchPointerUp(MouseButton.LEFT, x + 120, y + 90);
            assertFalse(node.pressed());
            assertEquals(0.25f, root.sliderDisplayValue(node));

            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            enabled.set(false);
            root.update(0);
            assertFalse(node.pressed());
            assertFalse(node.hovered());
            assertEquals(0.25f, root.sliderDisplayValue(node));
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            assertFalse(input.dispatchPointerDown(MouseButton.LEFT, x, y));
            input.dispatchPointerUp(MouseButton.LEFT, x, y);

            enabled.set(true);
            root.update(0);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            assertTrue(node.pressed());
            root.input(null);
            assertFalse(node.pressed());
            assertFalse(node.hovered());
            assertEquals(0.25f, root.sliderDisplayValue(node));
        } finally {
            root.dispose();
        }
    }

    @Test
    void customSurfaceInModalShowsFeedbackWithoutInputOrFocusCallbacks() {
        DefaultInput input = new DefaultInput();
        UiRoot root = preview(input);
        UiNode[] surface = new UiNode[1];
        UiSurfaceInput callbacks = new UiSurfaceInput() {
            public UiPointerResult pointer(UiPointerEvent event) {
                fail("Preview dispatched a custom pointer event");
                return UiPointerResult.IGNORED;
            }

            public boolean keyDown(KeyEvent event) {
                return fail("Preview dispatched a custom key event");
            }

            public boolean textInput(TextInputEvent event) {
                return fail("Preview dispatched a custom text event");
            }

            public void focusChanged(boolean focused) {
                fail("Preview changed custom surface focus");
            }

        };
        root.setContent(ui -> ui.modal(Ui.modal("preview"), modal ->
                surface[0] = modal.custom("surface", Ui.modifier().size(100, 60).focusable(true),
                        custom -> custom.input(callbacks))));
        try {
            root.update(0);
            UiNode node = surface[0];
            int x = Math.round(node.bounds().x() + 10);
            int y = Math.round(node.bounds().y() + 10);
            input.dispatchPointerMoved(x, y);
            assertTrue(node.hovered());
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            assertTrue(node.pressed());
            input.dispatchPointerMoved(x + 120, y);
            assertFalse(node.pressed());
            input.dispatchPointerMoved(x, y);
            assertTrue(node.pressed());
            input.dispatchScrolled(x, y, 0, 1);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            input.dispatchKeyDown(Key.ENTER);
            input.dispatchKeyDown(Key.ESCAPE);
            input.dispatchTextInput("test");
            assertFalse(node.pressed());
            assertFalse(node.focused());
            assertTrue(node.visible());
        } finally {
            root.dispose();
        }
    }

    private static UiRoot preview(DefaultInput input) {
        UiRoot root = new UiToolkit(null).previewRoot(null, null).allowFontFallback(true).input(input);
        root.resize(320, 240);
        return root;
    }

}
