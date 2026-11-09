package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.COMPACT_CHECKBOX_SIZE;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.checkboxLabel;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;

/** Demonstrates the centered popup and modal layers. */
public final class UiKitOverlaysSection implements UiContent {
    private final Runnable incrementClick;
    public final UiBooleanState showPopup = Ui.state(false);
    public final UiBooleanState showModal = Ui.state(false);
    public final UiBooleanState blockPopupInput = Ui.state(false);

    public UiKitOverlaysSection(Runnable incrementClick) {
        this.incrementClick = incrementClick;
    }

    @Override
    public void build(UiScope content) {
        content.text("Overlays", Ui.modifier().style("title"));
        content.text("Layer modes", Ui.modifier().style("muted"));
        content.row(Ui.modifier().gap(8.0f), row -> {
            row.button("Popup", () -> showPopup.toggle());
            row.button("Modal", () -> showModal.set(true));
        });
        content.text("Centered popup and modal", Ui.modifier().style("small"));
    }

    public void buildOverlays(UiScope page) {
        buildPopup(page);
        buildModal(page);
    }

    private void buildPopup(UiScope page) {
        page.popup(Ui.popup("uikit-popup").align(UiAlign.CENTER, UiAlign.CENTER)
                .dismissOnOutsidePress(false).blockingInput(blockPopupInput.get()), popup -> {
                    popup.animatedVisibility(showPopup.get(), Ui.animation().durationMillis(80).fade().slideY(-10.0f),
                            animated -> animated.panel(Ui.modifier().width(280.0f).padding(12.0f).gap(7.0f)
                                    .style("popup-panel"), this::buildPopupContent));
                });
    }

    private void buildPopupContent(UiScope panel) {
        panel.text("popup", Ui.modifier().style("section"));
        panel.text("centered overlay content", Ui.modifier().style("small"));
        checkboxLabel(panel, "Block input", blockPopupInput, COMPACT_CHECKBOX_SIZE);
        panel.row(Ui.modifier().gap(6.0f), row -> {
            row.button("Keep", incrementClick);
            row.button("Close", Ui.modifier().validationId(UiKitValidationIds.POPUP_CLOSE_BUTTON),
                    () -> showPopup.set(false));
        });
    }

    private void buildModal(UiScope page) {
        if (showModal.get()) {
            page.modal(Ui.modal(UiKitValidationIds.MODAL_ID).scrim(UiColor.rgba(0.0f, 0.0f, 0.0f, 0.55f)), modal -> {
                modal.panel(Ui.modifier().width(300.0f).padding(12.0f).gap(8.0f).style("nine-panel"), panel -> {
                    panel.text("modal", Ui.modifier().style("title"));
                    panel.text("overlay layer and scrim", Ui.modifier().style("muted"));
                    panel.row(Ui.modifier().gap(6.0f), row -> {
                        row.button("Close", () -> showModal.set(false));
                        row.button("Keep", incrementClick);
                    });
                });
            });
        }
    }

}
