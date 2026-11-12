package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcaseThemes.textStyle;

import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiScope;
import java.util.function.BooleanSupplier;

/** Demonstrates custom drawing and layered images. */
public final class UiKitDrawingSection implements UiContent {
    private static final float DRAWING_TEXT_SIZE = 22.0f;
    private static final float DRAWING_TEXT_MINIMUM = 12.0f;
    private static final float DRAWING_TEXT_MAXIMUM = 52.0f;

    private final UiFloatState sliderNormal;
    private final TextureRegion logoRegion;
    private final UiFloatState drawingTextSize = Ui.state(DRAWING_TEXT_SIZE);
    private final BooleanSupplier compactViewport;
    private float drawnVolume;

    public UiKitDrawingSection(UiFloatState sliderNormal, TextureRegion logoRegion, BooleanSupplier compactViewport) {
        this.sliderNormal = sliderNormal;
        this.logoRegion = logoRegion;
        this.compactViewport = compactViewport;
    }

    @Override
    public void build(UiScope content) {
        float drawnVolume = this.drawnVolume;
        content.text("Drawing", Ui.modifier().style("title"));
        content.text("Custom render areas", Ui.modifier().style("muted"));
        buildTextSizeControl(content);
        buildDrawingExamples(content, drawnVolume);
        buildImageOverlay(content);
    }

    private void buildTextSizeControl(UiScope content) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text("Resize this text", Ui.modifier().width(108.0f).style("muted"));
            row.slider(Ui.modifier().fillWidth().weight(1.0f).semanticLabel("Drawing text size"),
                    drawingTextSize, DRAWING_TEXT_MINIMUM, DRAWING_TEXT_MAXIMUM);
            row.text(Math.round(drawingTextSize.get()) + " px", Ui.modifier().width(52.0f).style("metric"));
        });
    }

    private void buildDrawingExamples(UiScope content, float drawnVolume) {
        if (compactViewport.getAsBoolean()) {
            buildBars(content, drawnVolume);
            buildTextExample(content);
            return;
        }
        content.row(Ui.modifier().fillWidth().gap(10.0f), row -> {
            buildBars(row, drawnVolume);
            buildTextExample(row);
        });
    }

    private void buildBars(UiScope row, float drawnVolume) {
        row.custom("draw-bars", Ui.modifier().width(260.0f).height(122.0f).style("tile"), custom -> {
            custom.draw((draw, bounds) -> {
                draw.rect(bounds, UiColor.rgba8888(0x121a23ff));
                draw.text("Bars", new UiRect(bounds.x() + 10.0f, bounds.y() + 8.0f,
                        bounds.width() - 20.0f, 18.0f), textStyle(13.0f, 0xa9b6c5ff));
                draw.rect(new UiRect(bounds.x() + 10.0f, bounds.y() + 40.0f,
                        (bounds.width() - 20.0f) * drawnVolume, 12.0f), UiColor.rgba8888(0x4da3ffff));
                draw.rect(new UiRect(bounds.x() + 10.0f, bounds.y() + 64.0f,
                        (bounds.width() - 20.0f) * sliderNormal.get(), 12.0f), UiColor.rgba8888(0x7bd88fff));
            });
        });
    }

    private void buildTextExample(UiScope row) {
        row.custom("draw-text", Ui.modifier().fillWidth()
                .height(Math.max(122.0f, drawingTextSize.get() * 2.0f + 54.0f)).style("tile"), custom -> {
            custom.draw((draw, bounds) -> {
                draw.rect(bounds, UiColor.rgba8888(0x101720ff));
                draw.text("FreeType " + Math.round(drawingTextSize.get()) + " px",
                        new UiRect(bounds.x() + 10.0f, bounds.y() + 8.0f, bounds.width() - 20.0f, 18.0f),
                        textStyle(13.0f, 0xa9b6c5ff));
                draw.text("Resize this text",
                        new UiRect(bounds.x() + 10.0f, bounds.y() + 42.0f,
                                bounds.width() - 20.0f, bounds.height() - 50.0f),
                        textStyle(drawingTextSize.get(), 0xf7fbffff));
            });
        });
    }

    private void buildImageOverlay(UiScope content) {
        content.stack(Ui.modifier().fillWidth().height(136.0f).style("stage"), stack -> {
            stack.image(logoRegion, Ui.modifier().fill());
            stack.panel(Ui.modifier().width(210.0f).align(UiAlign.END).padding(10.0f).gap(5.0f),
                    overlay -> {
                        overlay.text("Image layer", Ui.modifier().style("section"));
                        overlay.text("Logo with a panel overlay", Ui.modifier().style("small"));
                    });
        });
    }

    public void setDrawnVolume(float drawnVolume) {
        this.drawnVolume = drawnVolume;
    }

}
