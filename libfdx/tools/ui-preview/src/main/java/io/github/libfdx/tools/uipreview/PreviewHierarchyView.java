package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTreeItem;
import io.github.libfdx.ui.UiTreeState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Adapts the preview target catalog to the shared UI Kit tree. */
final class PreviewHierarchyView implements UiContent {
    private static final UiStyle BACKGROUND = UiStyle.style()
            .background(UiDrawable.color(UiColor.rgba8888(0x151a21ff)));
    private static final UiStyle TITLE = UiStyle.style().text(
            UiTextStyle.text().size(15).color(UiColor.rgba8888(0xe5ebf3ff)));
    private static final UiStyle FOOTER = UiStyle.style().text(
            UiTextStyle.text().size(12).color(UiColor.rgba8888(0x99a7b7ff)));

    private final String[] keys;
    private final UiTreeState tree;
    private final Consumer<String> select;
    private final UiBooleanState debugLines;
    private final PreviewRefreshStatus status;

    PreviewHierarchyView(List<PreviewHierarchy.Item> items, int active, IntConsumer select, UiBooleanState debugLines,
            PreviewRefreshStatus status) {
        this.debugLines = debugLines;
        this.status = status;
        keys = new String[items.size()];
        for (int index = 0; index < keys.length; index++) keys[index] = Integer.toString(index);
        tree = new UiTreeState(treeItems(items, 0, new int[1]));
        tree.select(keys[active]);
        this.select = key -> select.accept(Integer.parseInt(key));
    }

    void active(int index) {
        tree.select(keys[index]);
    }

    int step(int index, boolean backward) {
        return Integer.parseInt(tree.nextVisibleKey(keys[index], backward));
    }

    @Override
    public void build(UiScope ui) {
        ui.panel(Ui.modifier().fill().padding(10, 12).gap(10).style(BACKGROUND), body -> {
            body.text("Preview hierarchy", Ui.modifier().style(TITLE));
            body.tree(Ui.modifier().fill().weight(1), tree, select);
            body.checkbox("Debug lines", Ui.modifier().fillWidth().height(26).style(TITLE)
                    .semanticLabel("Preview debug lines"), debugLines);
            body.text(status.text(), Ui.modifier().style(FOOTER));
        });
    }

    private UiTreeItem[] treeItems(List<PreviewHierarchy.Item> items, int depth, int[] next) {
        List<UiTreeItem> siblings = new ArrayList<>();
        while (next[0] < items.size() && items.get(next[0]).depth() == depth) {
            int index = next[0]++;
            siblings.add(new UiTreeItem(keys[index], items.get(index).label(), treeItems(items, depth + 1, next)));
        }
        return siblings.toArray(UiTreeItem[]::new);
    }

}
