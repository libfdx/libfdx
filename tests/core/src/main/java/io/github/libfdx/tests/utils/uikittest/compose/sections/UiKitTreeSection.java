package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.collections.OrderedMap;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTreeItem;
import io.github.libfdx.ui.UiTreeState;

/** Demonstrates tree items, independent disclosure and selection by key. */
public final class UiKitTreeSection implements UiContent {
    private final OrderedMap<String, UiBooleanState> included = new OrderedMap<>();
    private final UiState<String> lastAction = Ui.state("Use an item's small button to invoke its action.");
    private final UiStyle actionStyle = UiStyle.button().padding(0)
            .background(UiDrawable.color(UiColor.rgba8888(0x343941ff)))
            .text(UiTextStyle.text().size(14).align(UiTextAlign.CENTER).color(UiColor.WHITE));
    private final UiTreeItem[] items = {
            new UiTreeItem("project", "Project",
                    new UiTreeItem("scenes", "Scenes",
                            new UiTreeItem("scenes/menu", "Main menu"),
                            new UiTreeItem("scenes/level", "First level")),
                    new UiTreeItem("assets", "Assets",
                            new UiTreeItem("assets/logo", "logo.png"),
                            new UiTreeItem("assets/theme", "theme.png")),
                    new UiTreeItem("project/settings", "Settings")),
            new UiTreeItem("readme", "README.md")
    };
    private final UiTreeState tree = new UiTreeState(items);

    public UiKitTreeSection() {
        for (UiTreeItem item : items) retainCheckedState(item);
    }

    private void retainCheckedState(UiTreeItem item) {
        included.put(item.key(), Ui.state(false));
        for (int i = 0; i < item.children().size(); i++) retainCheckedState(item.children().get(i));
    }

    @Override
    public void build(UiScope content) {
        content.text("Tree", Ui.modifier().style("title"));
        content.text("Click a label to select it. Use the chevron to open or close a branch.",
                Ui.modifier().style("muted"));
        content.tree(Ui.modifier().fillWidth().height(260).semanticLabel("Project tree"), tree, null, this::buildItem);
        buildSelectionControls(content);
        content.text(lastAction.get(), Ui.modifier().style("small"));
    }

    private void buildItem(UiScope row, UiTreeItem item) {
        row.checkbox(Ui.modifier().size(20, 20).align(UiAlign.CENTER)
                .semanticLabel("Include " + item.label()), included.get(item.key()));
        row.text(item.label(), Ui.modifier().fillWidth().weight(1));
        row.button("...", Ui.modifier().size(22, 22).align(UiAlign.CENTER).style(actionStyle)
                .semanticLabel("Action for " + item.label()),
                () -> lastAction.set("Action invoked for " + item.label()));
    }

    private void buildSelectionControls(UiScope content) {
        content.row(Ui.modifier().fillWidth().gap(8), row -> {
            String key = tree.selectedKey();
            row.text(key == null ? "Select an item" : "Selected " + key,
                    Ui.modifier().fillWidth().weight(1).style("metric"));
            row.button("Select logo", () -> tree.select("assets/logo"));
            row.button("Clear", () -> tree.select(null));
        });
    }

}
