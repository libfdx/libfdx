package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiNinePatch;
import io.github.libfdx.ui.UiPressAnimation;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.ui.UiTextStyle;

/** Resizes a game skin while keeping its source corners and border thickness fixed. */
public final class UiKitNinePatchButton implements UiContent {
    private final UiFloatState width = Ui.state(164.0f);
    private final UiFloatState height = Ui.state(44.0f);
    private final UiStyle style;
    private final Runnable action;

    public UiKitNinePatchButton(TextureRegion background, Runnable action) {
        this.action = action;
        UiNinePatch skin = UiNinePatch.region(background, Ui.insets(280, 250, 280, 250), Ui.insets(0))
                .borderScale(1.0f / 16.0f);
        style = UiStyle.button().background(UiDrawable.ninePatch(skin))
                .text(UiTextStyle.text().size(18).color(UiColor.WHITE).wrap(false).align(UiTextAlign.CENTER))
                .pressAnimation(UiPressAnimation.create().scale(0.90f));
    }

    @Override
    public void build(UiScope ui) {
        ui.text("Nine-patch background", Ui.modifier().style("section"));
        buildDimension(ui, "Width", width, 100, 480);
        buildDimension(ui, "Height", height, 44, 160);
        ui.stack(Ui.modifier().fillWidth().height(176).padding(8), preview ->
                preview.button("Play", Ui.modifier().size(Math.round(width.get()), Math.round(height.get()))
                        .style(style).semanticLabel("Background texture example"), action));
        ui.text("Resize Play: corners stay fixed; edges and center stretch.", Ui.modifier().style("small"));
    }

    private void buildDimension(UiScope ui, String label, UiFloatState state, float minimum, float maximum) {
        ui.row(Ui.modifier().fillWidth().gap(8), row -> {
            row.text(label, Ui.modifier().width(54).style("muted"));
            row.slider(Ui.modifier().fillWidth().weight(1).semanticLabel("Play " + label.toLowerCase()),
                    state, minimum, maximum);
            row.text(Math.round(state.get()) + " px", Ui.modifier().width(54).style("metric"));
        });
    }

}
