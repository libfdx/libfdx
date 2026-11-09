package io.github.libfdx.tests.utils.uikittest.compose;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.percent;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_WINDOWS;

import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitDrawingSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTooltipsSection;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatAnimatable;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import java.util.function.Supplier;

/** Composes the page shell around the active section. */
public final class UiKitShowcaseContent implements UiContent {
    private static final float VALIDATION_NAV_SPACER_HEIGHT = 864.0f;
    private static final float SETTINGS_WIDTH = 270.0f;

    private final UiKitShowcase showcase;
    private final UiKitDrawingSection drawing;
    private final UiKitSettingsPanel settings;
    private final UiKitTooltipsSection tooltips;
    private final Runnable incrementClick;
    private final boolean previewMode;
    private final boolean validationActive;

    public UiKitShowcaseContent(UiKitShowcase showcase, UiKitDrawingSection drawing, UiKitSettingsPanel settings,
            UiKitTooltipsSection tooltips, Runnable incrementClick, boolean previewMode, boolean validationActive) {
        this.showcase = showcase;
        this.drawing = drawing;
        this.settings = settings;
        this.tooltips = tooltips;
        this.incrementClick = incrementClick;
        this.previewMode = previewMode;
        this.validationActive = validationActive;
    }

    public static UiContent deferred(Supplier<UiContent> content) {
        return ui -> content.get().build(ui);
    }

    @Override
    public void build(UiScope ui) {
        float drawnVolume = animateVolume(ui);
        drawing.setDrawnVolume(drawnVolume);
        boolean compact = showcase.compactViewport();
        ui.column(Ui.modifier().fill().gap(10.0f), page -> buildPage(page, compact, drawnVolume));
    }

    private float animateVolume(UiScope ui) {
        float currentVolume = showcase.volume();
        UiFloatAnimatable animatedVolume = ui.floatAnimatable("volume-meter", currentVolume);
        animatedVolume.animateTo(currentVolume, Ui.animation().durationMillis(180));
        return animatedVolume.get();
    }

    private void buildPage(UiScope page, boolean compact, float drawnVolume) {
        buildHeader(page, compact);
        if (previewMode) {
            buildPreviewHint(page);
        }
        page.row(Ui.modifier().fill().weight(1.0f).gap(10.0f), body -> buildBody(body, compact));
        buildLayers(page, drawnVolume);
    }

    private void buildHeader(UiScope page, boolean compact) {
        page.panel(Ui.modifier().fillWidth().height(62.0f).padding(12.0f).gap(6.0f).style("nine-panel"),
                header -> header.row(Ui.modifier().fillWidth().gap(10.0f), row -> buildHeaderRow(row, compact)));
    }

    private void buildHeaderRow(UiScope row, boolean compact) {
        row.text("UI KIT", Ui.modifier().width(compact ? 78.0f : 96.0f).style("title"));
        String sectionName = showcase.activeSectionName();
        row.text(compact ? sectionName : sectionName + "  |  " + statusText(showcase),
                Ui.modifier().fillWidth().weight(1.0f).style("muted"));
        if (!compact) {
            row.button("Press", incrementClick);
        }
        row.button("Modal", Ui.modifier().validationId(UiKitValidationIds.HEADER_MODAL_BUTTON),
                () -> showcase.overlays().showModal.set(true));
        row.button("Popup", () -> showcase.overlays().showPopup.toggle());
    }

    private void buildPreviewHint(UiScope page) {
        page.text("Preview  |  PgUp / PgDn changes section  |  Wheel scrolls the section",
                Ui.modifier().fillWidth().style("small"));
    }

    private void buildBody(UiScope body, boolean compact) {
        buildSectionNav(body, compact);
        buildActiveSection(body);
        if (!compact) {
            settings.buildPanel(body, SETTINGS_WIDTH);
        }
    }

    private void buildSectionNav(UiScope body, boolean compact) {
        float width = compact ? 152.0f : 188.0f;
        body.panel(Ui.modifier().width(width).fillHeight().padding(10.0f).gap(6.0f).style("nav-panel"),
                nav -> nav.scrollView(Ui.modifier().fill().weight(1.0f).semanticLabel("Sections nav"),
                        showcase.navScroll(), scroll -> buildSectionNavContents(scroll, compact)));
    }

    private void buildSectionNavContents(UiScope scroll, boolean compact) {
        scroll.text("sections", Ui.modifier().style("section"));
        for (int i = 0; i < showcase.sectionCount(); i++) {
            buildSectionButton(scroll, i);
        }
        if (compact) {
            scroll.divider(Ui.modifier().fillWidth());
            settings.buildControls(scroll, true);
        }
        if (validationActive) {
            scroll.spacer(Ui.modifier().height(VALIDATION_NAV_SPACER_HEIGHT));
        }
    }

    private void buildSectionButton(UiScope scroll, int section) {
        UiModifier modifier = Ui.modifier().fillWidth();
        if (showcase.activeSection().get() == section) {
            modifier = modifier.style("nav-active");
        }
        scroll.button(showcase.sectionLabel(section), modifier, () -> showcase.activeSection().set(section));
    }

    private void buildActiveSection(UiScope body) {
        body.panel(Ui.modifier().fill().weight(1.0f).padding(12.0f).gap(10.0f).style("section-panel"),
                content -> content.scrollView(Ui.modifier().fill().weight(1.0f).semanticLabel("Active section"),
                        showcase.activeSectionScroll(), showcase.activeSectionContent()));
    }

    private void buildLayers(UiScope page, float drawnVolume) {
        if (showcase.activeSection().get() == SECTION_WINDOWS) {
            showcase.windows().buildWindows(page, drawnVolume);
        }
        tooltips.buildTooltips(page);
        showcase.overlays().buildOverlays(page);
    }

    public static String statusText(UiKitShowcase showcase) {
        return "clicks " + showcase.clickCount() + "  checked " + showcase.checked()
                + "  volume " + percent(showcase.volume()) + "  name " + showcase.name();
    }

}
