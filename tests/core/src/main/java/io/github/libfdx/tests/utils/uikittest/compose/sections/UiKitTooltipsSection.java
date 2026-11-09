package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextInputFilter;
import java.util.function.BooleanSupplier;

/** Demonstrates tooltip targets and delayed tooltip layers. */
public final class UiKitTooltipsSection implements UiContent {
    private final BooleanSupplier compactViewport;
    private final Runnable incrementClick;
    private final UiBooleanState compactChecked;
    private final UiState<String> name;

    public UiKitTooltipsSection(BooleanSupplier compactViewport, Runnable incrementClick,
            UiBooleanState compactChecked, UiState<String> name) {
        this.compactViewport = compactViewport;
        this.incrementClick = incrementClick;
        this.compactChecked = compactChecked;
        this.name = name;
    }

    @Override
    public void build(UiScope content) {
        boolean compact = compactViewport.getAsBoolean();
        content.text("Tooltips", Ui.modifier().style("title"));
        content.text("Hover targets", Ui.modifier().style("muted"));
        buildHoverButtons(content);
        buildWidgetTargets(content, compact);
    }

    private void buildHoverButtons(UiScope content) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.button("Hover me", Ui.modifier().semanticLabel("Hover me"), incrementClick);
            row.button("Slow hint", Ui.modifier().semanticLabel("Slow hint"), incrementClick);
        });
    }

    private void buildWidgetTargets(UiScope content, boolean compact) {
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(12.0f).style("tile"), panel -> {
            UiContent tooltipTargets = targets -> buildTooltipTargets(targets, compact);
            if (compact) {
                panel.column(Ui.modifier().fillWidth().gap(8.0f), tooltipTargets);
            } else {
                panel.row(Ui.modifier().fillWidth().gap(18.0f), tooltipTargets);
            }
            panel.spacer(Ui.modifier().height(64.0f));
        });
    }

    private void buildTooltipTargets(UiScope targets, boolean compact) {
        buildTextTarget(targets, compact);
        buildCheckboxTarget(targets, compact);
        buildTextFieldTarget(targets);
    }

    private void buildTextTarget(UiScope targets, boolean compact) {
        UiModifier textTarget = compact ? Ui.modifier().fillWidth() : Ui.modifier().width(132.0f);
        targets.text("Text hover", textTarget.height(32.0f)
                .validationId(UiKitValidationIds.TOOLTIP_TEXT_TARGET)
                .tooltipTarget(UiKitValidationIds.TOOLTIP_TEXT_TARGET)
                .style("section"));
    }

    private void buildCheckboxTarget(UiScope targets, boolean compact) {
        UiModifier checkboxTarget = compact ? Ui.modifier().fillWidth() : Ui.modifier().width(184.0f);
        targets.checkbox("Checkbox hover",
                checkboxTarget.validationId(UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET)
                        .tooltipTarget(UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET)
                        .style("small"),
                compactChecked);
    }

    private void buildTextFieldTarget(UiScope targets) {
        targets.textField(Ui.modifier().fillWidth().weight(1.0f)
                        .height(32.0f)
                        .validationId(UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET)
                        .tooltipTarget(UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET)
                        .semanticLabel("Tooltip text field"),
                name, UiTextInputFilter.STRING);
    }

    public void buildTooltips(UiScope page) {
        buildTooltip(page, "Hover me", 0, 164.0f, "tooltip", "button hover");
        buildTooltip(page, "Slow hint", 650, 178.0f, "delayed tooltip", "alpha fade-in");
        buildTooltip(page, UiKitValidationIds.TOOLTIP_TEXT_TARGET, 2000, 190.0f,
                "text tooltip", "shown after 2 seconds");
        buildTooltip(page, UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET, 2000, 210.0f,
                "checkbox tooltip", "shown after 2 seconds");
        buildTooltip(page, UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET, 2000, 216.0f,
                "text field tooltip", "shown after 2 seconds");
    }

    private void buildTooltip(UiScope page, String key, int delayMillis, float width, String title, String description) {
        page.tooltip(Ui.tooltip(key).delayMillis(delayMillis).align(UiAlign.START),
                tooltip -> tooltip.panel(Ui.modifier().width(width).padding(6.0f).gap(3.0f).style("tooltip-panel"),
                        panel -> {
                            panel.text(title, Ui.modifier().style("section"));
                            panel.text(description, Ui.modifier().style("small"));
                        }));
    }

}
