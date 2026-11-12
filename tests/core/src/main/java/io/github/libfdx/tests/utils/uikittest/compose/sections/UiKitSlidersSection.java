package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.percent;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.signedValue;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.sliderValue;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcaseThemes.textStyle;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiSize;
import java.util.function.BooleanSupplier;

/** Demonstrates slider ranges, sizes, and live values. */
public final class UiKitSlidersSection implements UiContent {
    private final UiFloatState volume;
    private final BooleanSupplier compactViewport;
    private final UiFloatState sliderNormal;
    private final UiFloatState sliderCompact = Ui.state(0.60f);
    private final UiFloatState sliderWide;
    private final UiFloatState sliderNegative = Ui.state(0.0f);
    private final UiFloatState sliderDisabled;
    private final UiFloatState sliderTiny = Ui.state(0.30f);
    private final UiFloatState sliderPrecise = Ui.state(0.125f);

    public UiKitSlidersSection(UiFloatState volume, BooleanSupplier compactViewport,
            UiFloatState sliderNormal, UiFloatState sliderWide, UiFloatState sliderDisabled) {
        this.volume = volume;
        this.compactViewport = compactViewport;
        this.sliderNormal = sliderNormal;
        this.sliderWide = sliderWide;
        this.sliderDisabled = sliderDisabled;
    }

    @Override
    public void build(UiScope content) {
        content.text("Sliders", Ui.modifier().style("title"));
        buildRangeExamples(content);
        buildSizeExamples(content);
        buildPreview(content);
    }

    private void buildRangeExamples(UiScope content) {
        content.text("Variants", Ui.modifier().style("muted"));
        sliderRow(content, "Volume", volume, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Volume setting")
                        .validationId(UiKitValidationIds.SLIDER_VOLUME));
        sliderRow(content, "Normal", sliderNormal, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Normal slider"));
        sliderRow(content, "Wide", sliderWide, 0.0f, 100.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Wide slider"));
        sliderRow(content, "Negative", sliderNegative, -1.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Negative slider"));
        sliderRow(content, "Precise", sliderPrecise, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Precise slider"));
        sliderRow(content, "Disabled", sliderDisabled, 0.0f, 1.0f,
                Ui.modifier().fillWidth().weight(1.0f).enabled(false).semanticLabel("Disabled slider"));
    }

    private void buildSizeExamples(UiScope content) {
        if (compactViewport.getAsBoolean()) {
            sliderCard(content, "Compact", sliderCompact, 0.0f, 1.0f,
                    Ui.modifier().width(130.0f).semanticLabel("Compact slider"));
            sliderCard(content, "Tiny", sliderTiny, 0.0f, 1.0f,
                    Ui.modifier().width(82.0f).semanticLabel("Tiny slider"));
        } else {
            content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
                sliderCard(row, "Compact", sliderCompact, 0.0f, 1.0f,
                        Ui.modifier().width(130.0f).semanticLabel("Compact slider"));
                sliderCard(row, "Tiny", sliderTiny, 0.0f, 1.0f,
                        Ui.modifier().width(82.0f).semanticLabel("Tiny slider"));
            });
        }
    }

    private void buildPreview(UiScope content) {
        content.custom("slider-preview", Ui.modifier().fillWidth().height(90.0f), custom -> {
            custom.measure(bounds -> new UiSize(Math.min(520.0f, bounds.maxWidth()), 90.0f));
            custom.draw((draw, bounds) -> {
                UiRect track = new UiRect(bounds.x() + 10.0f, bounds.y() + 18.0f,
                        Math.max(1.0f, bounds.width() - 20.0f), 14.0f);
                float normalizedNegative = (sliderNegative.get() + 1.0f) * 0.5f;
                draw.rect(bounds, UiColor.rgba8888(0x0d1117ff));
                draw.rect(track, UiColor.rgba8888(0x283342ff));
                draw.rect(new UiRect(track.x(), track.y(), track.width() * sliderNormal.get(), track.height()),
                        UiColor.rgba8888(0x4da3ffff));
                draw.rect(new UiRect(track.x(), track.y() + 24.0f, track.width() * normalizedNegative,
                        track.height()), UiColor.rgba8888(0x7bd88fff));
                draw.text("normal " + percent(sliderNormal.get()) + "  negative "
                                + signedValue(sliderNegative.get()),
                        new UiRect(bounds.x() + 10.0f, bounds.y() + 62.0f, bounds.width() - 20.0f, 18.0f),
                        textStyle(13.0f, 0xc7d4e3ff));
            });
        });
    }

    private void sliderRow(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier sliderModifier) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(78.0f).style("muted"));
            row.slider(sliderModifier, state, minimum, maximum);
            row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(54.0f).style("metric"));
        });
    }

    private void sliderCard(UiScope content, String label, UiFloatState state, float minimum, float maximum,
            UiModifier sliderModifier) {
        UiModifier cardModifier = compactViewport.getAsBoolean()
                ? Ui.modifier().fillWidth().padding(8.0f).gap(6.0f).style("tile")
                : Ui.modifier().width(220.0f).padding(8.0f).gap(6.0f).style("tile");
        content.panel(cardModifier, card -> {
            card.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
                row.text(label, Ui.modifier().fillWidth().weight(1.0f).style("muted"));
                row.text(sliderValue(state.get(), minimum, maximum), Ui.modifier().width(48.0f).style("metric"));
            });
            card.slider(sliderModifier, state, minimum, maximum);
        });
    }

}
