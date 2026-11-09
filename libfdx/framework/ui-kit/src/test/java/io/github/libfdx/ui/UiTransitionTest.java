package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import org.junit.jupiter.api.Test;

final class UiTransitionTest {
    @Test
    void transitionsAdvanceDeterministicallyWithoutRecompositionOrRelayout() {
        UiRoot root = root();
        UiNode[] panel = new UiNode[1];
        int[] compositions = {0};
        root.setContent(ui -> {
            compositions[0]++;
            panel[0] = ui.panel(Ui.modifier().size(100, 80).transition("show",
                    Ui.transition().durationMillis(1000).easing(UiEasing.linear()).alpha(0, 1).scale(0.5f, 1)), null);
        });
        try {
            root.update(0);
            UiRect bounds = panel[0].bounds();
            assertEquals(0, panel[0].renderAlpha());
            assertEquals(0.5f, panel[0].transitionScale());
            root.update(0.25f);
            assertEquals(0.25f, panel[0].renderAlpha(), 0.001f);
            assertEquals(0.625f, panel[0].transitionScale(), 0.001f);
            assertEquals(18.75f, panel[0].renderX(bounds.x()), 0.001f);
            assertEquals(62.5f, panel[0].renderWidth(bounds.width()), 0.001f);
            assertSame(bounds, panel[0].bounds());
            assertEquals(1, compositions[0]);
            root.update(0.75f);
            assertEquals(1, panel[0].renderAlpha());
            assertEquals(1, panel[0].transitionScale());
        } finally { root.dispose(); }
    }

    @Test
    void sameStatePreservesProgressAndChangedStateRestartsAfterCompletion() {
        UiRoot root = root();
        UiState<String> state = Ui.state("first");
        UiNode[] panel = new UiNode[1];
        root.setContent(ui -> panel[0] = ui.panel(Ui.modifier().size(100, 80).transition(state.get(),
                Ui.transition().durationMillis(1000).easing(UiEasing.linear()).alpha(0, 1)), null));
        try {
            root.update(0.25f);
            root.requestCompose();
            root.update(0);
            assertEquals(0.25f, panel[0].renderAlpha(), 0.001f);
            state.set("interrupted");
            root.update(0);
            assertEquals(0.25f, panel[0].renderAlpha(), 0.001f);
            root.update(1);
            state.set("second");
            root.update(0);
            assertEquals(0, panel[0].renderAlpha());
            root.update(0.5f);
            assertEquals(0.5f, panel[0].renderAlpha(), 0.001f);
        } finally { root.dispose(); }
    }

    @Test
    void removingTransitionOrItsNodeReleasesAnimationAndRespectsAnimationScale() {
        UiRoot root = root();
        UiIntState mode = Ui.state(0);
        UiNode[] panel = new UiNode[1];
        root.animationScale(0.5f);
        root.setContent(ui -> {
            if (mode.get() < 2) panel[0] = ui.panel(Ui.modifier().size(100, 80).alpha(0.8f)
                    .transition("show", mode.get() == 0 ? Ui.transition().durationMillis(1000)
                            .easing(UiEasing.linear()).alpha(0, 1) : null), null);
        });
        try {
            root.update(0.5f);
            assertEquals(0.2f, panel[0].renderAlpha(), 0.001f);
            mode.set(1);
            root.update(0);
            assertEquals(0.8f, panel[0].renderAlpha(), 0.001f);
            UiNode removed = panel[0];
            mode.set(2);
            root.update(0);
            assertTrue(removed.isDisposed());
            mode.set(0);
            root.update(0);
            assertNotSame(removed, panel[0]);
            assertEquals(0, panel[0].renderAlpha());
        } finally { root.dispose(); }
    }

    @Test
    void scaledDescendantsUseTheSameGeometryForRenderingAndHitTesting() {
        UiRoot root = root();
        UiNode[] button = new UiNode[1];
        int[] clicks = {0};
        root.setContent(ui -> ui.panel(Ui.modifier().size(200, 100).transition("scaled",
                Ui.transition().scale(0.5f, 0.5f)), panel ->
                button[0] = panel.button("Hit", Ui.modifier().fill(), () -> clicks[0]++)));
        try {
            root.update(0);
            assertNull(root.hitTest(25, 50));
            assertSame(button[0], root.hitTest(75, 50));
            root.handlePointerDown(PointerEvent.button(0, MouseButton.LEFT, 75, 50));
            root.handlePointerUp(PointerEvent.button(1, MouseButton.LEFT, 75, 50));
            assertEquals(1, clicks[0]);
            assertEquals(50, button[0].renderX(0), 0.001f);
            assertEquals(25, button[0].renderY(0), 0.001f);
            assertEquals(100, button[0].renderWidth(200), 0.001f);
        } finally { root.dispose(); }
    }

    @Test
    void sliderInputUsesTheInverseScaleTransform() {
        UiRoot root = root();
        UiFloatState value = Ui.state(0.0f);
        UiNode[] slider = new UiNode[1];
        root.setContent(ui -> ui.panel(Ui.modifier().size(200, 100).transition("scaled",
                Ui.transition().scale(0.5f, 0.5f)), panel ->
                slider[0] = panel.slider(Ui.modifier().fill(), value, 0, 1)));
        try {
            root.update(0);
            UiRect bounds = slider[0].bounds();
            int x = Math.round(slider[0].renderX(bounds.x() + 8 + (bounds.width() - 16) * 0.75f));
            int y = Math.round(slider[0].renderY(bounds.y() + bounds.height() * 0.5f));
            root.handlePointerDown(PointerEvent.button(0, MouseButton.LEFT, x, y));
            root.handlePointerUp(PointerEvent.button(1, MouseButton.LEFT, x, y));
            assertEquals(0.75f, value.get(), 0.02f);
        } finally { root.dispose(); }
    }

    private static UiRoot root() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        root.resize(320, 240);
        return root;
    }
}
