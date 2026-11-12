package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiNinePatch;
import io.github.libfdx.ui.UiPressAnimation;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiShape;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTextAlign;
import java.util.function.BooleanSupplier;

/** Small examples of shared shapes, texture surfaces, and button content. */
public final class UiKitButtonCustomization implements UiContent {
    private final UiStyle rounded;
    private final UiStyle circle;
    private final UiStyle composed;
    private final UiKitNinePatchButton ninePatch;
    private final BooleanSupplier compactViewport;
    private final UiStyle checkbox;
    private final TextureRegion logo;
    private final Runnable action;
    private final UiBooleanState included = Ui.state(true);

    public UiKitButtonCustomization(TextureRegion texture, TextureRegion logo, TextureRegion buttonBackground,
            Runnable action, BooleanSupplier compactViewport) {
        this.logo = logo;
        this.action = action;
        this.compactViewport = compactViewport;
        ninePatch = new UiKitNinePatchButton(buttonBackground, action);
        UiStyle base = UiStyle.button().background(UiDrawable.color(UiColor.rgba8888(0x326899ff)))
                .text(UiTextStyle.text().size(15).color(UiColor.WHITE).wrap(false).align(UiTextAlign.CENTER));
        rounded = base.shape(UiShape.rounded(12))
                .hover(base.background(UiDrawable.color(UiColor.rgba8888(0x4283bbff))))
                .pressed(base.background(UiDrawable.color(UiColor.rgba8888(0x21496fff))));
        circle = rounded.shape(UiShape.circle()).padding(0);
        composed = base.shape(UiShape.rounded(12))
                .background(UiDrawable.ninePatch(UiNinePatch.region(texture, Ui.insets(5), Ui.insets(0))))
                .pressAnimation(UiPressAnimation.create());
        checkbox = UiStyle.style().background(UiDrawable.texture(texture))
                .foreground(UiDrawable.texture(logo)).padding(2)
                .text(UiTextStyle.text().size(14).color(UiColor.WHITE));
    }

    @Override
    public void build(UiScope ui) {
        ui.text("Shapes and composable content", Ui.modifier().style("muted"));
        if (compactViewport.getAsBoolean()) {
            buildShapeButtons(ui);
            buildComposedButton(ui);
        } else {
            ui.row(Ui.modifier().fillWidth().gap(12), row -> {
                buildShapeButtons(row);
                buildComposedButton(row);
            });
        }
        ui.checkbox("Textured checkbox indicator", Ui.modifier().style(checkbox), included);
        ninePatch.build(ui);
    }

    private void buildShapeButtons(UiScope ui) {
        ui.row(Ui.modifier().gap(12), row -> {
            row.button("Rounded", Ui.modifier().width(132).height(44).style(rounded)
                    .semanticLabel("Rounded example"), action);
            row.button("+", Ui.modifier().size(48, 48).style(circle)
                    .semanticLabel("Circle example"), action);
        });
    }

    private void buildComposedButton(UiScope ui) {
        ui.button(Ui.modifier().width(184).height(44).gap(8).clip().style(composed)
                .semanticLabel("Composed content example"), action, this::buildButtonContent);
    }

    private void buildButtonContent(UiScope content) {
        content.image(logo, Ui.modifier().size(26, 26));
        content.text("Image + text", Ui.modifier().fillWidth().weight(1));
    }

}
