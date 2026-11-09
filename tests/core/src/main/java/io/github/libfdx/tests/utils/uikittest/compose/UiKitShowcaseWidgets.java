package io.github.libfdx.tests.utils.uikittest.compose;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;

/** Shared widget rows and value labels for the showcase. */
public final class UiKitShowcaseWidgets {
    public static final float DEFAULT_CHECKBOX_SIZE = 20.0f;
    public static final float COMPACT_CHECKBOX_SIZE = 18.0f;

    private UiKitShowcaseWidgets() {
    }

    public static void checkboxLabel(UiScope ui, String label, UiBooleanState state) {
        checkboxLabel(ui, label, state, Float.NaN);
    }

    public static void checkboxLabel(UiScope ui, String label, UiBooleanState state, float checkboxSize) {
        checkboxLabel(ui, label, state, checkboxSize, true);
    }

    public static void checkboxLabel(UiScope ui, String label, UiBooleanState state, float checkboxSize, boolean enabled) {
        ui.row(Ui.modifier().gap(6.0f), row -> {
            UiModifier checkboxModifier = Ui.modifier().semanticLabel(label).enabled(enabled);
            String validationId = checkboxValidationId(label);
            if (validationId != null) {
                checkboxModifier = checkboxModifier.validationId(validationId);
            }
            if (!Float.isNaN(checkboxSize)) {
                checkboxModifier = checkboxModifier.size(checkboxSize, checkboxSize);
            }
            row.checkbox(checkboxModifier, state);
            row.text(label, Ui.modifier().enabled(enabled));
        });
    }

    private static String checkboxValidationId(String label) {
        if ("Section option".equals(label)) {
            return UiKitValidationIds.CHECKBOX_SECTION_OPTION;
        }
        if ("Block input".equals(label)) {
            return UiKitValidationIds.POPUP_BLOCK_INPUT;
        }
        return null;
    }

    public static void progressRow(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier progressModifier) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(78.0f).style("muted"));
            row.progressBar(progressModifier, state, minimum, maximum);
            row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(54.0f).style("metric"));
        });
    }

    public static String scaleValue(float value) {
        return Math.round(value * 100.0f) / 100.0f + "x";
    }

    public static String percent(float value) {
        return Math.round(value * 100.0f) + "%";
    }

    public static String sliderValue(float value, float minimum, float maximum) {
        if (maximum == 100.0f) {
            return String.valueOf(Math.round(value));
        }
        if (minimum < 0.0f) {
            return signedValue(value);
        }
        return percent(value);
    }

    public static String signedValue(float value) {
        int rounded = Math.round(value * 100.0f);
        if (rounded > 0) {
            return "+" + rounded + "%";
        }
        return rounded + "%";
    }

}
