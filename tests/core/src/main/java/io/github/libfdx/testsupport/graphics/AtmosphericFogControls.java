package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.ui.*;

/** Matching comparison controls for the two atmospheric fog demonstrations. */
public final class AtmosphericFogControls implements Disposable {
    public final UiBooleanState enabled = Ui.state(true);
    public final UiBooleanState autoWalk = Ui.state(false);
    public final UiFloatState strength = Ui.state(1f);
    public final UiFloatState circleRadius = Ui.state(6f);
    private final UiRoot root;

    public AtmosphericFogControls(Fdx fdx, String title, String description, String hint, boolean showCircleRadius, Runnable reset) {
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf", 18))
                .size(18).lineHeight(24).color(UiColor.rgba8888(0xf0f5f8ff));
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .style("fog-controls", UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(0x101e28e6)))))
                .root(fdx.displays().main(), fdx.graphics().main()).input(fdx.input());
        root.setContent(ui -> ui.column(Ui.modifier().fill().padding(22).gap(6), page -> {
            page.panel(Ui.modifier().fillWidth().gap(6).style("fog-controls"), header -> {
                header.text(title, Ui.modifier().fillWidth().minHeight(28));
                header.text(description, Ui.modifier().fillWidth().minHeight(26));
                header.text(hint, Ui.modifier().fillWidth().minHeight(26));
            });
            page.spacer(Ui.modifier().weight(1));
            page.panel(Ui.modifier().width(440).gap(6).style("fog-controls"), footer -> {
                footer.row(Ui.modifier().fillWidth().gap(10).height(38), row -> {
                    row.checkbox(Ui.modifier().semanticLabel("Atmospheric fog enabled"), enabled);
                    row.text("Fog", Ui.modifier().width(44));
                    row.button(autoWalk.get() ? "Pause walk" : "Auto walk", Ui.modifier().width(156),
                            () -> autoWalk.set(!autoWalk.get()));
                    row.button("Reset", Ui.modifier().width(92), reset);
                });
                footer.row(Ui.modifier().fillWidth().gap(10).height(32), row -> {
                    row.text("Fog strength", Ui.modifier().width(120));
                    row.slider(Ui.modifier().width(240).semanticLabel("Fog strength"), strength, 0, 1);
                    row.text(Math.round(strength.get() * 100) + "%", Ui.modifier().width(56));
                });
                if (showCircleRadius) footer.row(Ui.modifier().fillWidth().gap(10).height(32), row -> {
                    row.text("Circle radius", Ui.modifier().width(120));
                    row.slider(Ui.modifier().width(240).semanticLabel("Circle radius"), circleRadius, 3, 16);
                    row.text(Float.toString(Math.round(circleRadius.get() * 10) / 10f), Ui.modifier().width(56));
                });
            });
        }));
    }

    public void render(float delta) { root.update(delta); root.render(); }
    public void resize(int width, int height) { root.resize(width, height); }
    @Override public boolean isDisposed() { return root.isDisposed(); }
    @Override public void dispose() { root.dispose(); }
}
