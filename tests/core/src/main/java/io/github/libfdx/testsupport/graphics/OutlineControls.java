package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.ui.*;

/** Shared pixel-width control for the outline demonstrations. */
public final class OutlineControls {
    public final UiFloatState width = Ui.state(Float.parseFloat(System.getProperty("libfdx.test.outlineWidth", "3")));
    private final UiRoot root;

    public OutlineControls(Fdx fdx, String title, String description) {
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf", 18))
                .size(18).lineHeight(24).color(UiColor.rgba8888(0xe9f2ffff));
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .style("outline-panel", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(0x101c2bee)))))
                .root(fdx.displays().main(), fdx.graphics().main()).input(fdx.input());
        root.setContent(ui -> ui.column(Ui.modifier().fill().padding(22).gap(6), page -> {
            page.panel(Ui.modifier().fillWidth().gap(4).style("outline-panel"), panel -> {
                panel.text(title);
                panel.text(description);
            });
            page.spacer(Ui.modifier().weight(1));
            page.panel(Ui.modifier().width(440).style("outline-panel"), panel ->
                panel.row(Ui.modifier().fillWidth().gap(12).height(36), row -> {
                    row.text("Stroke size", Ui.modifier().width(100));
                    row.slider(Ui.modifier().width(220).semanticLabel("Outline stroke size in pixels"), width, 0, 12);
                    row.text(Math.round(width.get() * 10) / 10f + " px", Ui.modifier().width(68));
                }));
        }));
    }

    public void update(float delta) { root.update(delta); }
    public void render() { root.render(); }
    public void resize(int width, int height) { root.resize(width, height); }
    public void dispose() { root.dispose(); }
}
