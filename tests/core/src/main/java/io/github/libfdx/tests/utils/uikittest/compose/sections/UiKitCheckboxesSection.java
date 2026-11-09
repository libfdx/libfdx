package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.checkboxLabel;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;

/** Demonstrates labeled and bare checkbox states. */
public final class UiKitCheckboxesSection implements UiContent {
    private final UiBooleanState checked;
    private final UiBooleanState compactChecked;
    private final UiBooleanState visualDebug;
    private final UiBooleanState bareChecked = Ui.state(false);
    private final UiBooleanState disabledChecked = Ui.state(true);

    public UiKitCheckboxesSection(UiBooleanState checked, UiBooleanState compactChecked, UiBooleanState visualDebug) {
        this.checked = checked;
        this.compactChecked = compactChecked;
        this.visualDebug = visualDebug;
    }

    @Override
    public void build(UiScope content) {
        content.text("Checkboxes", Ui.modifier().style("title"));
        buildLabeledExamples(content);
        buildBareExamples(content);
        buildStatus(content);
    }

    private void buildLabeledExamples(UiScope content) {
        content.text("States", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(18.0f), row -> {
            checkboxLabel(row, "Compact option", compactChecked, 16.0f);
            checkboxLabel(row, "Section option", checked);
            checkboxLabel(row, "Disabled", disabledChecked, 18.0f, false);
        });
    }

    private void buildBareExamples(UiScope content) {
        content.panel(Ui.modifier().width(150.0f).padding(10.0f).gap(7.0f).style("tile"), panel -> {
            panel.text("Bare boxes", Ui.modifier().style("section"));
            panel.row(Ui.modifier().gap(10.0f), row -> {
                row.checkbox(Ui.modifier().size(16.0f, 16.0f).semanticLabel("Bare checkbox A"), bareChecked);
                row.checkbox(Ui.modifier().size(20.0f, 20.0f).semanticLabel("Bare checkbox B"), compactChecked);
            });
        });
    }

    private void buildStatus(UiScope content) {
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(5.0f).style("tile"), panel -> {
            panel.text("checked " + checked.get(), Ui.modifier().style("small"));
            panel.text("visual debug " + visualDebug.get(), Ui.modifier().style("small"));
        });
    }

}
