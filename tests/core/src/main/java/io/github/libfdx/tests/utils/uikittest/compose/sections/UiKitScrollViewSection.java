package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextInputFilter;

/** Demonstrates a fixed-height scroll view with interactive children. */
public final class UiKitScrollViewSection implements UiContent {
    private final UiState<String> name;
    private final Runnable incrementClick;
    public final UiScrollState mediumScroll = new UiScrollState();

    public UiKitScrollViewSection(UiState<String> name, Runnable incrementClick) {
        this.name = name;
        this.incrementClick = incrementClick;
    }

    @Override
    public void build(UiScope content) {
        content.text("Scroll view", Ui.modifier().style("title"));
        content.text("Medium scroll container", Ui.modifier().style("muted"));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(8.0f).style("tile"), this::buildScrollPanel);
    }

    private void buildScrollPanel(UiScope panel) {
        panel.text("Scroll the area to reach the hidden rows", Ui.modifier().style("section"));
        panel.text("This container is fixed height and keeps interaction widgets inside it.",
                Ui.modifier().style("small"));
        panel.scroll(Ui.modifier().fillWidth().height(260.0f).padding(8.0f).gap(8.0f).style("scroll")
                        .semanticLabel("Medium scroll body"),
                mediumScroll, this::buildScrollContents);
    }

    private void buildScrollContents(UiScope scroll) {
        scroll.textField(Ui.modifier().fillWidth().semanticLabel("Scroll text field"), name, UiTextInputFilter.STRING);
        for (int item = 1; item <= 24; item++) {
            buildScrollRow(scroll, item);
        }
    }

    private void buildScrollRow(UiScope scroll, int item) {
        scroll.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text("Row " + item, Ui.modifier().width(56.0f).style("muted"));
            row.text("Scrollable widget sample " + item, Ui.modifier().fillWidth().weight(1.0f).style("small"));
            row.button("Action", Ui.modifier().width(72.0f), incrementClick);
        });
    }

}
