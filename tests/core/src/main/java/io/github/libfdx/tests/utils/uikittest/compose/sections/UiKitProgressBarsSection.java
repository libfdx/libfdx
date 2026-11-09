package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.percent;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.progressRow;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiScope;

/** Mirrors slider state in read-only progress bars. */
public final class UiKitProgressBarsSection implements UiContent {
    private final UiFloatState volume;
    private final UiFloatState sliderWide;
    private final UiFloatState sliderDisabled;

    public UiKitProgressBarsSection(UiFloatState volume, UiFloatState sliderWide, UiFloatState sliderDisabled) {
        this.volume = volume;
        this.sliderWide = sliderWide;
        this.sliderDisabled = sliderDisabled;
    }

    @Override
    public void build(UiScope content) {
        content.text("Progress bars", Ui.modifier().style("title"));
        buildStateMirrors(content);
        buildFixedProgress(content);
    }

    private void buildStateMirrors(UiScope content) {
        content.text("State mirrors", Ui.modifier().style("muted"));
        progressRow(content, "Volume", volume, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Volume progress")
                        .validationId(UiKitValidationIds.PROGRESS_VOLUME));
        progressRow(content, "Wide", sliderWide, 0.0f, 100.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Wide progress")
                        .validationId(UiKitValidationIds.PROGRESS_WIDE));
        progressRow(content, "Disabled", sliderDisabled, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).enabled(false).semanticLabel("Disabled progress"));
    }

    private void buildFixedProgress(UiScope content) {
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(6.0f).style("tile"), panel -> {
            panel.text("read-only progress", Ui.modifier().style("section"));
            panel.progressBar(Ui.modifier().fillWidth().semanticLabel("Fixed progress"), 0.64f);
            panel.text("fixed " + percent(0.64f), Ui.modifier().style("small"));
        });
    }

}
