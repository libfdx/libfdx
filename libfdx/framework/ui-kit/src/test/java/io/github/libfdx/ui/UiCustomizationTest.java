package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import org.junit.jupiter.api.Test;

final class UiCustomizationTest {
    @Test
    void shapesClampCornersAndFitCirclesInsideNonSquareBounds() {
        UiShape rounded = UiShape.rounded(100);
        UiRect wide = new UiRect(10, 20, 100, 40);
        assertFalse(rounded.contains(wide, 10, 20));
        assertTrue(rounded.contains(wide, 60, 21));
        assertFalse(UiShape.circle().contains(wide, 15, 40));
        assertTrue(UiShape.circle().contains(wide, 60, 40));
        assertTrue(UiShape.circle().contains(wide, 60, 20));
        assertFalse(UiShape.circle().contains(new UiRect(0, 0, 40, 100), 20, 5));
        assertFalse(rounded.contains(UiRect.ZERO, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> UiShape.rounded(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> UiShape.rounded(-1));
        assertSame(rounded, UiStyle.button().shape(rounded).padding(4).hover(UiStyle.button()).shape());
    }

    @Test
    void buttonContentMeasuresHorizontallyAndNestedActionsConsumeTheirOwnInput() {
        UiRoot root = root(500, 180);
        UiNode[] nodes = new UiNode[3];
        int[] actions = new int[2];
        UiStyle style = UiStyle.button().text(UiTextStyle.text().color(UiColor.BLACK));
        try {
            root.setContent(ui -> nodes[0] = ui.button(Ui.modifier().gap(8).style(style), () -> actions[0]++,
                    content -> {
                        nodes[1] = content.text("Content wider than a default button");
                        nodes[2] = content.button("...", Ui.modifier().size(24, 24), () -> actions[1]++);
                    }));
            root.update(0);
            assertTrue(nodes[0].bounds().width() > 240);
            assertTrue(nodes[1].bounds().right() <= nodes[2].bounds().x());
            assertTrue(nodes[2].bounds().right() <= nodes[0].bounds().right());
            assertSame(style.textStyle(), root.textStyleFor(nodes[1]));
            click(root, nodes[2]);
            assertEquals(0, actions[0]);
            assertEquals(1, actions[1]);
            click(root, nodes[1]);
            assertEquals(1, actions[0]);
            root.requestCompose();
            UiNode text = nodes[1];
            root.update(0);
            assertSame(text, nodes[1]);
        } finally {
            root.dispose();
        }
    }

    @Test
    void shapedClippingAndHitTestingAgreeForScaledChildren() {
        UiRoot root = root(200, 150);
        UiNode[] button = new UiNode[1];
        UiStyle circular = UiStyle.button().padding(0).shape(UiShape.circle());
        try {
            root.setContent(ui -> ui.panel(Ui.modifier().size(100, 100).padding(0).clip().style(circular)
                            .transition("scaled", Ui.transition().scale(0.5f, 0.5f)),
                    panel -> button[0] = panel.button("Inside", Ui.modifier().fill().style(circular), () -> {})));
            root.update(0);
            UiNode node = button[0];
            assertNull(root.hitTest(node.renderX(1), node.renderY(1)));
            assertSame(node, root.hitTest(node.renderX(50), node.renderY(50)));
            UiSurfaceClip clip = new UiSurfaceClip();
            clip.node(root, node);
            assertTrue(clip.active());
            clip.span(1);
            assertTrue(clip.left > 30);
            assertTrue(clip.right < 70);
        } finally {
            root.dispose();
        }
    }

    @Test
    void shapedClippingStopsAtOverlayBoundariesAndRejectsZeroScale() {
        UiRoot root = root(200, 150);
        UiNode[] content = new UiNode[2];
        UiStyle shape = UiStyle.style().shape(UiShape.circle());
        UiSurfaceClip clip = new UiSurfaceClip();
        try {
            root.setContent(ui -> ui.panel(Ui.modifier().size(100, 100).clip().style(shape), panel -> {
                content[0] = panel.text("Clipped content");
                panel.popup(UiPopup.popup("actions"), popup -> content[1] = popup.text("Overlay content"));
            }));
            root.update(0);
            clip.node(root, content[0]);
            assertTrue(clip.active());
            clip.node(root, content[1]);
            assertFalse(clip.active());

            root.setContent(ui -> ui.panel(Ui.modifier().size(100, 100).clip().style(shape)
                            .transition("hidden", Ui.transition().scale(0, 0)),
                    panel -> content[0] = panel.text("Hidden content")));
            root.update(0);
            clip.node(root, content[0]);
            clip.span(20);
            assertTrue(clip.right <= clip.left);
        } finally {
            root.dispose();
        }
    }

    @Test
    void customTreeControlsKeepSelectionIndependentAndValuesSurviveCollapse() {
        UiRoot root = root(300, 160);
        UiTreeState tree = new UiTreeState(new UiTreeItem("root", "Root", new UiTreeItem("leaf", "Leaf")));
        UiBooleanState checked = Ui.state(false);
        UiNode[] controls = new UiNode[3];
        int[] actions = new int[1];
        try {
            root.setContent(ui -> ui.tree(Ui.modifier().fill(), tree, null, (row, item) -> {
                if (item.key().equals("leaf")) {
                    controls[0] = row.checkbox(Ui.modifier().size(20, 20), checked);
                    controls[1] = row.text(item.label(), Ui.modifier().fillWidth().weight(1));
                    controls[2] = row.button("...", Ui.modifier().size(22, 22), () -> actions[0]++);
                } else {
                    row.text(item.label());
                }
            }));
            tree.select("root");
            root.update(0);
            click(root, controls[0]);
            assertTrue(checked.get());
            assertEquals("root", tree.selectedKey());
            click(root, controls[2]);
            assertEquals(1, actions[0]);
            assertEquals("root", tree.selectedKey());
            click(root, controls[1]);
            assertEquals("leaf", tree.selectedKey());
            tree.setExpanded("root", false);
            root.update(0.3f);
            tree.setExpanded("root", true);
            root.update(0.3f);
            assertTrue(checked.get());
            assertTrue(controls[0].checked());
            assertEquals("leaf", tree.selectedKey());
        } finally {
            root.dispose();
        }
    }

    private static UiRoot root(int width, int height) {
        UiRoot root = new UiRoot(null, null, null, null).allowFontFallback(true);
        root.resize(width, height);
        return root;
    }

    private static void click(UiRoot root, UiNode node) {
        UiRect bounds = node.bounds();
        int x = Math.round(node.renderX(bounds.x() + bounds.width() * 0.5f));
        int y = Math.round(node.renderY(bounds.y() + bounds.height() * 0.5f));
        root.handlePointerDown(PointerEvent.button(1, MouseButton.LEFT, x, y));
        root.handlePointerUp(PointerEvent.button(2, MouseButton.LEFT, x, y));
        root.update(0);
    }

}
