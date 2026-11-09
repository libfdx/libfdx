package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.progressRow;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;

/** Demonstrates determinate and indeterminate loading indicators. */
public final class UiKitLoadingSection implements UiContent {
    private final UiFloatState volume;
    private final UiStyle inlineLoadingStyle = UiStyle.style()
            .background(UiDrawable.color(UiColor.rgba8888(0x331b4dff)))
            .foreground(UiDrawable.color(UiColor.rgba8888(0xe56bffff)));

    public UiKitLoadingSection(UiFloatState volume) {
        this.volume = volume;
    }

    @Override
    public void build(UiScope content) {
        content.text("Loading & status", Ui.modifier().style("title"));
        buildDeterminateProgress(content);
        buildLoadingPanel(content);
        buildInlineLoadingBar(content);
    }

    private void buildDeterminateProgress(UiScope content) {
        content.text("Determinate", Ui.modifier().style("muted"));
        progressRow(content, "Volume", volume, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Loading determinate progress"));
    }

    private void buildLoadingPanel(UiScope content) {
        content.divider(Ui.modifier().fillWidth());
        content.text("Indeterminate", Ui.modifier().style("muted"));
        content.panel(Ui.modifier().fillWidth().padding(12.0f).gap(12.0f).style("tile"), panel -> {
            panel.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
                row.loadingSpinner(Ui.modifier().size(36.0f, 36.0f)
                        .semanticLabel("Loading spinner")
                        .validationId(UiKitValidationIds.LOADING_SPINNER));
                row.column(Ui.modifier().fillWidth().weight(1.0f).gap(6.0f), details -> {
                    details.text("Synchronizing UI assets", Ui.modifier().style("section"));
                    details.loadingBar(Ui.modifier().fillWidth().height(12.0f)
                            .semanticLabel("Loading bar")
                            .validationId(UiKitValidationIds.LOADING_BAR));
                    details.text("Animation is driven by UiRoot elapsed time", Ui.modifier().style("small"));
                });
            });
        });
    }

    private void buildInlineLoadingBar(UiScope content) {
        content.loadingBar(Ui.modifier().fillWidth().height(16.0f).style(inlineLoadingStyle)
                .semanticLabel("Inline styled loading bar"));
    }

}
