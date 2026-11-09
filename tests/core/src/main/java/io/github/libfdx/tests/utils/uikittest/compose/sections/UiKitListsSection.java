package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiListState;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;

/** Demonstrates scrollable and virtualized lists. */
public final class UiKitListsSection implements UiContent {
    static final String[] ACTIVITY = new String[] {
            "preload assets",
            "hover row",
            "focus input",
            "drag slider",
            "submit text"
    };
    static final String[] INVENTORY = new String[] {
            "med kit",
            "blue key",
            "map",
            "torch",
            "part",
            "flare"
    };
    private static final ArrayView<String> ACTIVITY_ITEMS = Array.<String>of(ACTIVITY).view();
    private static final ArrayView<String> INVENTORY_ITEMS = Array.<String>of(INVENTORY).view();

    private final UiScrollState feedScroll = new UiScrollState();
    public final UiListState inventoryList = new UiListState();
    private final UiNode[] virtualLists = new UiNode[2];

    public UiKitListsSection() {
        feedScroll.scrollTo(0.0f, 8.0f);
        inventoryList.scrollToItem(1, 0.0f);
    }

    @Override
    public void build(UiScope content) {
        content.text("Lists", Ui.modifier().style("title"));
        content.text("Scrollable data", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
            buildActivityFeed(row);
            buildInventoryLists(row);
        });
    }

    private void buildActivityFeed(UiScope row) {
        row.scroll(Ui.modifier().width(300.0f).height(190.0f).padding(8.0f).gap(5.0f).style("scroll"),
                feedScroll, scroll -> {
                    scroll.text("scroll y=" + Math.round(feedScroll.y()), Ui.modifier().style("small"));
                    scroll.items(ACTIVITY_ITEMS, item -> "feed-" + item,
                            (itemUi, item) -> itemUi.text(item, Ui.modifier().style("small")));
                });
    }

    private void buildInventoryLists(UiScope row) {
        row.panel(Ui.modifier().width(230.0f).height(190.0f).padding(8.0f).gap(5.0f).style("tile"), list -> {
            list.text("virtual list", Ui.modifier().style("section"));
            virtualLists[0] = list.virtualList(INVENTORY_ITEMS, inventoryList, 4, item -> item,
                    (itemUi, item) -> itemUi.text(item, Ui.modifier().style("small")));
            virtualLists[1] = list.virtualList(INVENTORY_ITEMS, null, 2, item -> item,
                    (itemUi, item) -> itemUi.text(item, Ui.modifier().style("small")));
        });
    }

    public UiNode virtualList(int index) {
        return virtualLists[index];
    }

    public static String inventoryItem(int index) {
        return INVENTORY[index];
    }

}
