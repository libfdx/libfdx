package io.github.libfdx.tests.utils.uikittest.compose;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.COMPACT_CHECKBOX_SIZE;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.checkboxLabel;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.scaleValue;

import io.github.libfdx.display.Display;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;

/** Composes the settings sidebar or its inline controls on compact layouts. */
public final class UiKitSettingsPanel {
    private static final float UI_SCALE_MINIMUM = 0.75f;
    private static final float UI_SCALE_MAXIMUM = 2.25f;

    private final Display display;
    private final UiBooleanState visualDebug;
    private final UiBooleanState scaleGlobally;
    private final UiFloatState uiScale;
    private final UiScrollState settingsScroll = new UiScrollState();

    public UiKitSettingsPanel(Display display, UiBooleanState visualDebug,
            UiBooleanState scaleGlobally, UiFloatState uiScale) {
        this.display = display;
        this.visualDebug = visualDebug;
        this.scaleGlobally = scaleGlobally;
        this.uiScale = uiScale;
    }

    void buildPanel(UiScope body, float width) {
        body.panel(Ui.modifier().width(width).fillHeight().padding(10.0f).gap(8.0f).style("nav-panel"), settings -> {
            settings.scrollView(Ui.modifier().fill().weight(1.0f), settingsScroll, scroll -> {
                buildControls(scroll, false);
            });
        });
    }

    void buildControls(UiScope content, boolean compact) {
        content.text("settings", Ui.modifier().style("section"));
        buildContentScale(content, compact);
        buildDebugToggles(content);
        buildTextSizeControl(content, compact);
    }

    private void buildContentScale(UiScope content, boolean compact) {
        if (compact) {
            content.text("Content scale " + contentScaleValue(), Ui.modifier().style("small"));
        } else {
            content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                row.text("Content scale", Ui.modifier().fillWidth().weight(1.0f).style("muted"));
                row.text(contentScaleValue(), Ui.modifier().width(64.0f).style("metric"));
            });
        }
    }

    private void buildDebugToggles(UiScope content) {
        checkboxLabel(content, "Visual debug", visualDebug, COMPACT_CHECKBOX_SIZE);
        checkboxLabel(content, "Scale globally", scaleGlobally);
    }

    private void buildTextSizeControl(UiScope content, boolean compact) {
        if (compact) {
            content.slider(Ui.modifier().fillWidth().enabled(scaleGlobally.get())
                            .semanticLabel("Text size setting")
                            .validationId(UiKitValidationIds.SETTINGS_TEXT_SIZE_SLIDER),
                    uiScale, UI_SCALE_MINIMUM, UI_SCALE_MAXIMUM);
        } else {
            content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                row.text("Text size", Ui.modifier().width(78.0f).style("muted").enabled(scaleGlobally.get()));
                row.slider(Ui.modifier().fillWidth().weight(1.0f).enabled(scaleGlobally.get())
                                .semanticLabel("Text size setting")
                                .validationId(UiKitValidationIds.SETTINGS_TEXT_SIZE_SLIDER),
                        uiScale, UI_SCALE_MINIMUM, UI_SCALE_MAXIMUM);
                row.text(scaleValue(uiScale.get()), Ui.modifier().width(48.0f).style("metric")
                        .enabled(scaleGlobally.get()));
            });
        }
    }

    private String contentScaleValue() {
        return scaleValue(display != null ? display.contentScale() : 1.0f);
    }

}
