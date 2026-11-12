package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import org.junit.jupiter.api.Test;

final class UiPressAnimationTest {
    @Test
    void pressSurvivesRecompositionAndAnimatesContentWithoutChangingLayoutOrInput() {
        UiRoot root = root();
        UiNode[] nodes = new UiNode[3];
        int[] compositions = {0};
        int[] clicks = {0};
        UiStyle style = UiStyle.button().pressAnimation(UiPressAnimation.create().scale(0.9f).brightness(0.7f));
        root.setContent(ui -> {
            compositions[0]++;
            nodes[0] = ui.button(Ui.modifier().size(200, 60).style(style), () -> clicks[0]++, content -> {
                nodes[1] = content.text("Content", Ui.modifier().weight(1).fillWidth());
                nodes[2] = content.button("+", Ui.modifier().size(24, 24), () -> {});
            });
        });
        try {
            root.update(0);
            UiRect bounds = nodes[0].bounds();
            root.handlePointerDown(pointer(5, 30));
            root.update(0.08f);
            assertTrue(nodes[0].pressed());
            assertTrue(nodes[0].hovered());
            assertEquals(180, nodes[0].renderWidth(200), 0.01f);
            assertEquals(0.7f, nodes[1].renderBrightness(), 0.01f);
            assertSame(nodes[0], root.hitTest(5, 30));
            UiRect inner = nodes[2].bounds();
            assertSame(nodes[2], root.hitTest(inner.x() + 12, inner.y() + 12));
            int settledCompositions = compositions[0];
            root.update(0.02f);
            assertEquals(settledCompositions, compositions[0]);
            assertSame(bounds, nodes[0].bounds());
            root.requestCompose();
            root.update(0);
            assertTrue(nodes[0].pressed());
            assertEquals(180, nodes[0].renderWidth(200), 0.01f);
            root.handlePointerUp(pointer(5, 30));
            assertEquals(1, clicks[0]);
            root.update(0.3f);
            assertEquals(200, nodes[0].renderWidth(200), 0.01f);
            assertEquals(1, nodes[1].renderBrightness(), 0.01f);
        } finally {
            root.dispose();
        }
    }

    @Test
    void quickClicksAndKeyboardActivationProduceACompletePulse() {
        UiRoot root = root();
        UiNode[] button = new UiNode[1];
        int[] clicks = {0};
        root.setContent(ui -> button[0] = ui.button("Play", Ui.modifier().size(200, 60)
                .style(UiStyle.button().pressAnimation(UiPressAnimation.create())), () -> clicks[0]++));
        try {
            root.update(0);
            root.handlePointerDown(pointer(100, 30));
            root.handlePointerUp(pointer(100, 30));
            assertEquals(1, clicks[0]);
            root.update(0.04f);
            assertTrue(button[0].renderWidth(200) < 195);
            root.update(1);
            assertEquals(200, button[0].renderWidth(200), 0.01f);
            root.handleKeyDown(new KeyEvent(2, Key.ENTER, false));
            assertEquals(2, clicks[0]);
            root.update(0.04f);
            assertTrue(button[0].renderWidth(200) < 195);
            root.update(1);
            assertEquals(200, button[0].renderWidth(200), 0.01f);
        } finally {
            root.dispose();
        }
    }

    @Test
    void removingFeedbackOrDisablingTheButtonCancelsRetainedAnimation() {
        UiRoot root = root();
        UiBooleanState enabled = Ui.state(true);
        UiBooleanState animate = Ui.state(true);
        UiNode[] button = new UiNode[1];
        UiStyle style = UiStyle.button().pressAnimation(UiPressAnimation.create());
        root.setContent(ui -> button[0] = ui.button("Play", Ui.modifier().size(200, 60).enabled(enabled.get())
                .style(animate.get() ? style : style.pressAnimation(null)), () -> {}));
        try {
            root.update(0);
            root.handlePointerDown(pointer(100, 30));
            root.update(0.04f);
            root.animationScale(0);
            float width = button[0].renderWidth(200);
            root.update(1);
            assertEquals(width, button[0].renderWidth(200));
            animate.set(false);
            root.update(0);
            assertEquals(200, button[0].renderWidth(200));
            animate.set(true);
            root.update(0);
            enabled.set(false);
            root.update(0);
            assertFalse(button[0].pressed());
            assertFalse(button[0].hovered());
            assertEquals(200, button[0].renderWidth(200));
        } finally {
            root.dispose();
        }
    }

    private static UiRoot root() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        root.resize(320, 180);
        return root;
    }

    private static PointerEvent pointer(int x, int y) {
        return PointerEvent.button(1, MouseButton.LEFT, x, y);
    }

}
