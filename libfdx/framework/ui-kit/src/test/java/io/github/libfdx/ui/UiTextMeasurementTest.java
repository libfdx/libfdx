package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class UiTextMeasurementTest {
    @Test
    void fractionalLabelWidthsDoNotWrapAgainWhenPlacedFarFromOrigin() {
        BitmapFont font = font();
        UiRoot root = new UiRoot(null, null, null, null);
        UiNode[] text = new UiNode[1];
        UiTextStyle style = UiTextStyle.text().font(UiFont.bitmap(font)).size(15.3f);
        try {
            root.resize(1440, 600);
            root.setContent(ui -> ui.row(Ui.modifier().fillWidth(), row -> {
                row.spacer(Ui.modifier().width(719.7f));
                row.panel(Ui.modifier().padding(6), panel ->
                        text[0] = panel.text("count", Ui.modifier().style(UiStyle.style().text(style))));
            }));
            root.update(0);
            UiNode label = text[0];
            // Recreate the renderer's available width after placement at fractional coordinates.
            float width = label.bounds().right() - label.bounds().x();
            assertEquals(1, root.textLayout(label, label.text(), style, width).lines().size());
            assertTrue(width >= font.width("count", 15.3f));
        } finally {
            root.dispose();
            font.dispose();
        }
    }

    @Test
    void wrappedTextMeasuresInsideExplicitAndMaximumPanelWidthsIncludingPadding() {
        BitmapFont font = font();
        UiRoot root = new UiRoot(null, null, null, null);
        UiTextStyle style = UiTextStyle.text().font(UiFont.bitmap(font)).size(10);
        UiNode[] text = new UiNode[2];
        try {
            root.resize(800, 600);
            root.setContent(ui -> ui.column(Ui.modifier().fillWidth(), column -> {
                column.panel(Ui.modifier().width(100).padding(10), panel ->
                        text[0] = panel.text("one two three four five", Ui.modifier().style(UiStyle.style().text(style))));
                column.panel(Ui.modifier().fillWidth().maxWidth(100).padding(10), panel ->
                        text[1] = panel.text("one two three four five", Ui.modifier().style(UiStyle.style().text(style))));
            }));
            root.update(0);
            for (UiNode label : text) {
                BitmapFontLayout drawn = root.textLayout(label, label.text(), style, label.bounds().width());
                assertTrue(drawn.lines().size() > 1);
                assertEquals(drawn.height(), label.bounds().height());
                assertTrue(label.bounds().width() <= 80);
            }
        } finally {
            root.dispose();
            font.dispose();
        }
    }

    private BitmapFont font() {
        Texture texture = (Texture)Proxy.newProxyInstance(Texture.class.getClassLoader(),
                new Class<?>[] {Texture.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "width" -> 256;
                    case "height" -> 16;
                    default -> throw new AssertionError(method.getName());
                });
        return BitmapFont.fromGrid(texture, "abcdefghijklmnopqrstuvwxyz? ", 7, 10);
    }

}
