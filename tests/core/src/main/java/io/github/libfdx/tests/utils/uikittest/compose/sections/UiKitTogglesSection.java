package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiScope;

/** Demonstrates switches and single-choice radio buttons. */
public final class UiKitTogglesSection implements UiContent {
    public final UiBooleanState switchEnabled = Ui.state(true);
    private final UiBooleanState switchCompact = Ui.state(false);
    public final UiIntState radioChoice = Ui.state(1);

    @Override
    public void build(UiScope content) {
        content.text("Toggles & choices", Ui.modifier().style("title"));
        buildSwitches(content);
        buildRadioChoices(content);
    }

    private void buildSwitches(UiScope content) {
        content.text("Switches", Ui.modifier().style("muted"));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(10.0f).style("tile"), panel -> {
            panel.toggleSwitch("Notifications",
                    Ui.modifier().fillWidth().semanticLabel("Notifications switch")
                            .validationId(UiKitValidationIds.SWITCH_NOTIFICATIONS),
                    switchEnabled);
            panel.toggleSwitch("Compact controls",
                    Ui.modifier().fillWidth().semanticLabel("Compact controls switch"),
                    switchCompact);
            panel.toggleSwitch("Disabled switch",
                    Ui.modifier().fillWidth().enabled(false).semanticLabel("Disabled switch"),
                    switchCompact);
        });
    }

    private void buildRadioChoices(UiScope content) {
        content.divider(Ui.modifier().fillWidth());
        content.text("Single-choice radio group", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(18.0f), row -> {
            row.radioButton("Balanced", Ui.modifier().semanticLabel("Balanced radio"), radioChoice, 0);
            row.radioButton("Quality",
                    Ui.modifier().semanticLabel("Quality radio")
                            .validationId(UiKitValidationIds.RADIO_QUALITY),
                    radioChoice, 1);
            row.radioButton("Performance", Ui.modifier().semanticLabel("Performance radio"), radioChoice, 2);
        });
        content.text("selected " + radioChoiceLabel(), Ui.modifier().style("small"));
    }

    private String radioChoiceLabel() {
        int selected = radioChoice.get();
        if (selected == 0) {
            return "Balanced";
        }
        if (selected == 2) {
            return "Performance";
        }
        if (selected == 3) {
            return "Nested";
        }
        return "Quality";
    }

}
