package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.percent;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.scaleValue;
import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitListsSection.ACTIVITY;
import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitListsSection.INVENTORY;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiListState;
import io.github.libfdx.ui.UiScope;

/** Demonstrates selectable tab content. */
public final class UiKitTabsSection implements UiContent {
    private final UiFloatState volume;
    private final UiIntState clickCount;
    private final UiListState inventoryList;
    private final UiFloatState uiScale;
    public final UiIntState activeDemoTab = Ui.state(0);

    public UiKitTabsSection(UiFloatState volume, UiIntState clickCount,
            UiListState inventoryList, UiFloatState uiScale) {
        this.volume = volume;
        this.clickCount = clickCount;
        this.inventoryList = inventoryList;
        this.uiScale = uiScale;
    }

    @Override
    public void build(UiScope content) {
        content.text("Tabs", Ui.modifier().style("title"));
        content.text("Selectable views", Ui.modifier().style("muted"));
        buildTabSelector(content);
        buildSelectedTab(content);
    }

    private void buildTabSelector(UiScope content) {
        content.tabs(Ui.modifier().fillWidth().semanticLabel("Demo tabs")
                        .validationId(UiKitValidationIds.TABS_DEMO).style("tabs"),
                activeDemoTab, "Overview", "Loadout", "Stats", "Log");
    }

    private void buildSelectedTab(UiScope content) {
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(5.0f).style("tile"), panel -> {
            int tab = activeDemoTab.get();
            panel.text("active tab " + demoTabLabel(tab), Ui.modifier().style("section"));
            if (tab == 0) {
                panel.text("Overview panel", Ui.modifier().style("small"));
                panel.text("volume " + percent(volume.get()) + "  clicks " + clickCount.get(),
                        Ui.modifier().style("small"));
            } else if (tab == 1) {
                panel.text("Loadout panel", Ui.modifier().style("small"));
                panel.text("item " + INVENTORY[inventoryList.firstVisibleIndex() % INVENTORY.length],
                        Ui.modifier().style("small"));
            } else if (tab == 2) {
                panel.text("Stats panel", Ui.modifier().style("small"));
                panel.text("scale " + scaleValue(uiScale.get()), Ui.modifier().style("small"));
            } else {
                panel.text("Log panel", Ui.modifier().style("small"));
                panel.text("latest " + ACTIVITY[0], Ui.modifier().style("small"));
            }
        });
    }

    private String demoTabLabel(int tab) {
        if (tab == 1) {
            return "Loadout";
        }
        if (tab == 2) {
            return "Stats";
        }
        if (tab == 3) {
            return "Log";
        }
        return "Overview";
    }

}
