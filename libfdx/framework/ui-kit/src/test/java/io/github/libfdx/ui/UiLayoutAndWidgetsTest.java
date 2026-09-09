package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import org.junit.jupiter.api.Test;

final class UiLayoutAndWidgetsTest {
    @Test
    void displayContentScaleIsAppliedByDefaultAndCanBeDisabled() {
        UiRoot root = new UiRoot(null, new ScaledDisplay(1.5f), null, null).allowFontFallback(true);

        assertTrue(root.autoUiScale());
        assertEquals(1.5f, root.effectiveUiScale(), 0.001f);
        assertEquals(10, root.displayX(10.0f));

        root.autoUiScale(false);

        assertFalse(root.autoUiScale());
        assertEquals(1.0f, root.effectiveUiScale(), 0.001f);
        assertEquals(20, root.displayX(30.0f));
        assertEquals(30.0f, root.uiX(20), 0.001f);
        root.dispose();
    }

    @Test
    void displayContentScaleChangePreservesLogicalLayoutWithoutWindowResize() {
        ScaledDisplay display = new ScaledDisplay(1.0f);
        UiRoot root = new UiRoot(null, display, null, null).allowFontFallback(true);
        root.setContent(scope -> scope.panel(Ui.modifier().fill(), null));
        root.update(0.0f);

        assertEquals(800.0f, root.rootNode().bounds().width(), 0.001f);
        assertEquals(600.0f, root.rootNode().bounds().height(), 0.001f);

        display.contentScale(2.0f);
        root.update(0.0f);

        assertEquals(800.0f, root.rootNode().bounds().width(), 0.001f);
        assertEquals(600.0f, root.rootNode().bounds().height(), 0.001f);
        assertEquals(2.0f, root.effectiveUiScale(), 0.001f);
        root.dispose();
    }

    @Test
    void desktopContentScaleAppliesWhenFramebufferMatchesWindowSize() {
        ScaledDisplay display = new ScaledDisplay(1.5f, false);
        UiRoot root = new UiRoot(null, display, null, null).allowFontFallback(true);
        root.setContent(scope -> scope.panel(Ui.modifier().fill(), null));
        root.update(0.0f);

        assertEquals(1.5f, root.effectiveUiScale(), 0.001f);
        assertEquals(534.0f, root.rootNode().bounds().width(), 0.001f);
        assertEquals(400.0f, root.rootNode().bounds().height(), 0.001f);
        assertEquals(30, root.displayX(20));
        assertEquals(20.0f, root.uiX(30), 0.001f);
        display.contentScale(2.0f);
        root.update(0.0f);
        assertEquals(400.0f, root.rootNode().bounds().width(), 0.001f);
        assertEquals(40, root.displayX(20));
        root.dispose();
    }

    @Test
    void scalingModesKeepPointerHitsAlignedWithFramebufferRendering() {
        for (boolean denseFramebuffer : new boolean[] {false, true}) {
            ScaledDisplay display = new ScaledDisplay(1.5f, denseFramebuffer);
            UiRoot root = new UiRoot(null, display, null, null).allowFontFallback(true);
            int[] clicks = {0};
            root.setContent(scope -> scope.button("Hit", Ui.modifier().size(100, 40), () -> clicks[0]++));
            for (boolean automatic : new boolean[] {true, false, true}) {
                root.autoUiScale(automatic).uiScale(1.25f);
                root.update(0);
                float expectedScale = automatic ? 1.875f : 1.25f;
                assertEquals(expectedScale, root.effectiveUiScale(), 0.001f);
                float density = denseFramebuffer ? 1.5f : 1.0f;
                // Derive the pointer location from physical pixels, independently of UI conversion helpers.
                int x = Math.round(90 * expectedScale / density);
                int y = Math.round(20 * expectedScale / density);
                int before = clicks[0];
                root.handlePointerDown(PointerEvent.button(0, MouseButton.LEFT, x, y));
                root.handlePointerUp(PointerEvent.button(1, MouseButton.LEFT, x, y));
                assertEquals(before + 1, clicks[0]);
                assertEquals(x, root.displayX(90));
                assertEquals(y, root.displayY(20));
            }
            root.dispose();
        }
    }

    @Test
    void visibleExplicitHeightRowDoesNotScrollWhenRequestedIntoView() {
        UiRoot root = new UiRoot(null, new ScaledDisplay(1.5f, false), null, null).allowFontFallback(true);
        UiScrollState scroll = new UiScrollState();
        UiNode[] rows = new UiNode[63];
        root.setContent(scope -> scope.scroll(Ui.modifier().fillWidth().height(200), scroll, list -> {
            for (int i = 0; i < rows.length; i++) {
                rows[i] = list.stack(Ui.modifier().fillWidth().height(51).margin(2),
                        item -> item.button("Test", Ui.modifier().fill(), () -> {}));
            }
        }));
        root.update(0);
        scroll.scrollTo(0, 58 * 51 - 70);
        root.resize(800, 600);
        float before = scroll.y();
        assertEquals(51, rows[58].bounds().y() - rows[57].bounds().y(), 0.001f);
        assertEquals(72, rows[58].bounds().y(), 0.001f);
        assertTrue(rows[58].bounds().bottom() < scroll.viewportHeight());
        assertFalse(root.ensureVisible(rows[58]));
        assertFalse(root.ensureVisible(rows[58]));
        assertEquals(before, scroll.y(), 0.001f);
        assertTrue(root.ensureVisible(rows[62]));
        assertEquals(200, rows[62].bounds().bottom(), 0.001f);
        assertFalse(root.ensureVisible(rows[62]));
        assertTrue(root.ensureVisible(rows[0]));
        assertEquals(0, rows[0].bounds().y(), 0.001f);
        root.dispose();
    }

    @Test
    void ensureVisibleRevealsPartiallyClippedNodesThroughNestedPaddedScrolls() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiScrollState outer = new UiScrollState();
        UiScrollState inner = new UiScrollState();
        UiNode[] target = new UiNode[1];
        root.resize(300, 300);
        root.setContent(scope -> scope.scroll(Ui.modifier().size(120, 120).padding(10), outer, list -> {
            list.panel(Ui.modifier().height(80), null);
            list.scroll(Ui.modifier().size(100, 100), inner, nested -> {
                nested.panel(Ui.modifier().height(80), null);
                target[0] = nested.button("Target", Ui.modifier().size(80, 40), () -> {});
            });
        }));
        root.update(0);
        assertTrue(root.ensureVisible(target[0]));
        assertEquals(20, inner.y(), 0.001f);
        assertEquals(80, outer.y(), 0.001f);
        assertEquals(110, target[0].bounds().bottom(), 0.001f);
        assertFalse(root.ensureVisible(target[0]));
        root.dispose();
    }

    @Test
    void ensureVisibleHandlesHorizontalOversizedAndDetachedNodes() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiScrollState scroll = new UiScrollState();
        UiNode[] nodes = new UiNode[2];
        root.resize(300, 200);
        root.setContent(scope -> scope.scroll(Ui.modifier().size(100, 100), scroll, list -> {
            nodes[0] = list.stack(Ui.modifier().size(400, 80), content -> {
                nodes[1] = content.button("Target", Ui.modifier().size(30, 40).offset(250, 0), () -> {});
            });
        }));
        root.update(0);
        assertFalse(root.ensureVisible(nodes[0]));
        assertTrue(root.ensureVisible(nodes[1]));
        assertEquals(180, scroll.x(), 0.001f);
        assertEquals(100, nodes[1].bounds().right(), 0.001f);
        assertFalse(root.ensureVisible(nodes[0]));
        assertFalse(root.ensureVisible(nodes[1]));
        assertFalse(root.ensureVisible(null));
        root.setContent(scope -> {});
        root.update(0);
        assertFalse(root.ensureVisible(nodes[1]));
        root.dispose();
    }

    @Test
    void rowShrinksFixedChildrenInsideConstrainedSpace() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiNode[] children = new UiNode[2];
        root.resize(120, 40);
        root.setContent(scope -> scope.row(UiModifier.none().fillWidth().height(40.0f).gap(8.0f), row -> {
            children[0] = row.panel(UiModifier.none().size(100.0f, 40.0f), null);
            children[1] = row.panel(UiModifier.none().size(100.0f, 40.0f), null);
        }));
        root.update(0.0f);

        assertContained(children[0], root.rootNode().bounds());
        assertContained(children[1], root.rootNode().bounds());
        assertTrue(children[0].bounds().width() < 100.0f);
        assertTrue(children[1].bounds().width() < 100.0f);
        root.dispose();
    }

    @Test
    void columnShrinksFixedChildrenWithoutNegativeOrOverflowingBounds() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiNode[] children = new UiNode[2];
        root.resize(80, 60);
        root.setContent(scope -> scope.column(UiModifier.none().fillWidth().fillHeight().gap(8.0f), column -> {
            children[0] = column.panel(UiModifier.none().size(80.0f, 50.0f), null);
            children[1] = column.panel(UiModifier.none().size(80.0f, 50.0f), null);
        }));
        root.update(0.0f);

        assertContained(children[0], root.rootNode().bounds());
        assertContained(children[1], root.rootNode().bounds());
        assertTrue(children[0].bounds().height() < 50.0f);
        assertTrue(children[1].bounds().height() < 50.0f);
        root.dispose();
    }

    @Test
    void gridPreferredHeightUsesRowMaximumsInsteadOfStackingEveryChild() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiNode[] nodes = new UiNode[2];
        root.resize(600, 300);
        root.setContent(scope -> {
            nodes[0] = scope.grid(3, Ui.modifier().fillWidth().gap(8.0f), grid -> {
                grid.panel(Ui.modifier().height(20.0f), null);
                grid.panel(Ui.modifier().height(30.0f), null);
                grid.panel(Ui.modifier().height(40.0f), null);
                grid.panel(Ui.modifier().height(50.0f), null);
                grid.panel(Ui.modifier().height(25.0f), null);
            });
            nodes[1] = scope.panel(Ui.modifier().height(10.0f), null);
        });
        root.update(0.0f);

        assertEquals(98.0f, nodes[0].bounds().height(), 0.001f);
        assertEquals(nodes[0].bounds().bottom(), nodes[1].bounds().y(), 0.001f);
        root.dispose();
    }

    @Test
    void switchRadioAndCollapseActivationUpdateTheirState() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiBooleanState switched = Ui.state(false);
        UiIntState selected = Ui.state(1);
        UiBooleanState expanded = Ui.state(false);
        UiNode[] nodes = new UiNode[3];
        root.resize(400, 240);
        root.setContent(scope -> {
            nodes[0] = scope.toggleSwitch("Switch", switched);
            nodes[1] = scope.radioButton("Second", selected, 2);
            nodes[2] = scope.collapseBar("Details", expanded, details -> details.text("Expanded"));
        });
        root.update(0.0f);

        assertFalse(nodes[0].checked());
        assertFalse(nodes[1].checked());
        assertFalse(nodes[2].checked());

        nodes[0].activate();
        nodes[1].activate();
        nodes[2].activate();
        root.update(0.0f);

        assertTrue(switched.get());
        assertEquals(2, selected.get());
        assertTrue(expanded.get());
        assertTrue(nodes[0].checked());
        assertTrue(nodes[1].checked());
        assertTrue(nodes[2].checked());
        assertTrue(nodes[0].bounds().height() >= 28.0f);
        assertTrue(nodes[1].bounds().height() >= 28.0f);
        assertEquals(1, nodes[2].children().size());
        assertTrue(nodes[2].children().get(0).bounds().y() >= nodes[2].bounds().y() + 44.0f);
        root.dispose();
    }

    @Test
    void radioGroupUsesOneTabStopAndArrowKeysChangeSelection() {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiIntState selected = Ui.state(0);
        UiNode[] choices = new UiNode[3];
        UiNode[] after = new UiNode[1];
        root.resize(480, 100);
        root.setContent(scope -> scope.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
            choices[0] = row.radioButton("First", selected, 0);
            choices[1] = row.radioButton("Second", selected, 1);
            choices[2] = row.radioButton("Third", selected, 2);
            after[0] = row.button("After", () -> { });
        }));
        root.update(0.0f);

        assertTrue(root.handleKeyDown(new KeyEvent(1L, Key.TAB, false)));
        assertTrue(choices[0].focused());
        assertTrue(root.handleKeyDown(new KeyEvent(2L, Key.RIGHT, false)));
        assertEquals(1, selected.get());
        assertTrue(choices[1].focused());
        assertTrue(root.handleKeyDown(new KeyEvent(3L, Key.TAB, false)));
        assertTrue(after[0].focused());
        root.dispose();
    }

    @Test
    void inlineStyleOverridesNamedAndDefaultThemeStyles() {
        UiStyle custom = UiStyle.button()
                .background(UiDrawable.color(UiColor.rgba8888(0x123456ff)))
                .foreground(UiDrawable.color(UiColor.rgba8888(0xabcdefff)));
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        UiNode[] button = new UiNode[1];
        root.resize(200, 80);
        root.setContent(scope -> button[0] = scope.button("Custom",
                UiModifier.none().style(custom), () -> { }));
        root.update(0.0f);

        assertSame(custom, root.styleFor(button[0]));
        assertSame(custom, button[0].modifier().inlineStyle());
        root.dispose();
    }

    @Test
    void builtInLightThemeKeepsControlTextDarkAndSingleLine() {
        UiTheme theme = UiTheme.light();

        assertTrue(theme.style("text-field").textStyle().color().red() < 0.5f);
        assertTrue(theme.style("button").textStyle().color().red() < 0.5f);
        assertFalse(theme.style("button").textStyle().wrap());
        assertTrue(theme.style("button").textStyle().ellipsis());
    }

    private static void assertContained(UiNode child, UiRect parent) {
        UiRect bounds = child.bounds();
        assertTrue(bounds.width() >= 0.0f);
        assertTrue(bounds.height() >= 0.0f);
        assertTrue(bounds.x() >= parent.x() - 0.001f);
        assertTrue(bounds.y() >= parent.y() - 0.001f);
        assertTrue(bounds.right() <= parent.right() + 0.001f,
                "Child right edge " + bounds.right() + " exceeded " + parent.right());
        assertTrue(bounds.bottom() <= parent.bottom() + 0.001f,
                "Child bottom edge " + bounds.bottom() + " exceeded " + parent.bottom());
    }

    private static final class ScaledDisplay implements Display {
        private float contentScale;
        private final boolean scaledFramebuffer;

        private ScaledDisplay(float contentScale) {
            this(contentScale, true);
        }

        private ScaledDisplay(float contentScale, boolean scaledFramebuffer) {
            this.contentScale = contentScale;
            this.scaledFramebuffer = scaledFramebuffer;
        }

        private void contentScale(float contentScale) {
            this.contentScale = contentScale;
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.of("ui-test");
        }

        @Override
        public <T> T as() {
            return null;
        }

        @Override
        public String title() {
            return "UI test";
        }

        @Override
        public void title(String title) {
        }

        @Override
        public int width() {
            return 800;
        }

        @Override
        public int height() {
            return 600;
        }

        @Override
        public int framebufferWidth() {
            return scaledFramebuffer ? Math.round(width() * contentScale) : width();
        }

        @Override
        public int framebufferHeight() {
            return scaledFramebuffer ? Math.round(height() * contentScale) : height();
        }

        @Override
        public float contentScale() {
            return contentScale;
        }

        @Override
        public boolean closeRequested() {
            return false;
        }

        @Override
        public void requestClose() {
        }
    }
}
