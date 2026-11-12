package io.github.libfdx.tests.utils.uikittest.compose;

import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitDrawingSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitWindowsSection;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;

/** The isolated canvas for one showcase section and its optional floating layer. */
public final class UiKitSectionContent implements UiContent {
    private final UiScrollState scroll = new UiScrollState();
    private final UiContent section;
    private final UiContent layers;

    public UiKitSectionContent(UiContent section) {
        this(section, null);
    }

    public UiKitSectionContent(UiContent section, UiContent layers) {
        this.section = section;
        this.layers = layers;
    }

    public static UiContent drawing(UiKitDrawingSection drawing, UiFloatState volume) {
        UiContent canvas = new UiKitSectionContent(drawing);
        return ui -> {
            drawing.setDrawnVolume(UiKitShowcaseContent.animateVolume(ui, volume.get()));
            canvas.build(ui);
        };
    }

    public static UiContent windows(UiKitWindowsSection windows, UiFloatState volume) {
        return new UiKitSectionContent(windows,
                ui -> windows.buildWindows(ui, UiKitShowcaseContent.animateVolume(ui, volume.get())));
    }

    @Override
    public void build(UiScope ui) {
        ui.panel(Ui.modifier().fill().padding(12).gap(10).style("section-panel"),
                panel -> panel.scrollView(Ui.modifier().fill().gap(10), scroll, section));
        if (layers != null) layers.build(ui);
    }

}
