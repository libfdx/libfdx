package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import org.junit.jupiter.api.Test;

final class UiLayoutAndWidgetsTest {
    @Test
    void rowShrinksFixedChildrenInsideConstrainedSpace() {
        UiRoot root = new UiRoot(null, null, null, null);
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
        UiRoot root = new UiRoot(null, null, null, null);
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
        UiRoot root = new UiRoot(null, null, null, null);
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
        UiRoot root = new UiRoot(null, null, null, null);
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
        UiRoot root = new UiRoot(null, null, null, null);
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
        UiRoot root = new UiRoot(null, null, null, null);
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
}
