package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiTreeTest {
    @Test
    void selectingALeafExpandsItsPathAndClosingABranchPreservesSelection() {
        UiTreeItem leaf = new UiTreeItem("logo", "logo.png");
        UiTreeState tree = new UiTreeState(
                new UiTreeItem("project", "Project", new UiTreeItem("assets", "Assets", leaf)),
                new UiTreeItem("readme", "README.md"));
        assertTrue(tree.isExpanded("project"));
        assertFalse(tree.isExpanded("assets"));
        assertEquals("readme", tree.nextVisibleKey("assets", false));

        tree.select("logo");
        assertTrue(tree.isExpanded("assets"));
        assertEquals("logo", tree.nextVisibleKey("assets", false));
        tree.setExpanded("project", false);
        tree.select("logo");
        assertFalse(tree.isExpanded("project"));
        assertEquals("logo", tree.selectedKey());
        assertEquals("readme", tree.nextVisibleKey("logo", false));
        tree.select(null);
        assertNull(tree.selectedKey());

        assertThrows(IllegalArgumentException.class, () -> new UiTreeState(leaf, leaf));
        assertThrows(IllegalArgumentException.class, () -> tree.select("missing"));
        assertNull(new UiTreeState().nextVisibleKey(null, false));
    }

    @Test
    void programmaticSelectionRevealsTheRowAndResizingFillsTheViewport() {
        UiTreeState tree = new UiTreeState(new UiTreeItem("project", "Project",
                new UiTreeItem("assets", "Assets", new UiTreeItem("logo", "logo.png")),
                new UiTreeItem("readme", "README.md")));
        UiRoot root = new UiToolkit(null).root(null, null).allowFontFallback(true);
        try {
            root.setContent(ui -> ui.tree(Ui.modifier().fill().gap(4), tree));
            root.resize(240, 52);
            root.update(0);
            tree.select("logo");
            root.resize(300, 52);
            root.update(0.3f);

            UiNode scroll = root.rootNode().children().get(0);
            UiNode selected = scroll.children().get(2);
            assertEquals(300.0f, selected.bounds().width());
            assertTrue(tree.scroll().y() > 0);
            assertTrue(selected.bounds().y() >= scroll.bounds().y());
            assertTrue(selected.bounds().bottom() <= scroll.bounds().bottom());
        } finally {
            root.dispose();
        }
    }

}
