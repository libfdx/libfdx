package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.ui.*;

/** Compact controls for exploration and the third-person camera. */
public final class FogOfWarControls3D implements Disposable {
    public final UiBooleanState dimExplored = Ui.state(true), autoWalk = Ui.state(false);
    private final UiRoot root;

    public FogOfWarControls3D(Fdx fdx, Runnable restart, Runnable lowCamera, Runnable overheadCamera) {
        UiTextStyle text = UiTextStyle.text().font(UiFont.freeType("font/freetype/lsans.ttf",18))
                .size(18).lineHeight(24).color(UiColor.rgba8888(0xe4edf5ff));
        root = new UiToolkit(fdx.files()).theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .style("fog-panel",UiStyle.style().background(UiDrawable.color(UiColor.rgba8888(0x101e28ed)))))
                .root(fdx.displays().main(),fdx.graphics().main()).input(fdx.input());
        root.setContent(ui -> ui.column(Ui.modifier().fill().padding(18).gap(4),page -> {
            page.panel(Ui.modifier().fillWidth().style("fog-panel"),header -> {
                header.text("FOG OF WAR 3D",Ui.modifier().height(26));
                header.text("WASD / arrows | Orbit: right-drag / touch | Wheel zoom");
            });
            page.spacer(Ui.modifier().weight(1));
            page.panel(Ui.modifier().width(550).gap(4).style("fog-panel"),panel -> {
                panel.row(Ui.modifier().gap(8).height(30),row -> {
                    row.checkbox(Ui.modifier().semanticLabel("Dim explored areas"),dimExplored);
                    row.text("Dim explored areas (50%)",Ui.modifier().width(400));
                });
                panel.row(Ui.modifier().gap(8).height(34),row -> {
                    row.button(autoWalk.get()?"Pause walk":"Auto walk",Ui.modifier().width(130),()->autoWalk.set(!autoWalk.get()));
                    row.button("Low camera",Ui.modifier().width(110),lowCamera);
                    row.button("Overhead",Ui.modifier().width(110),overheadCamera);
                    row.button("Restart",Ui.modifier().width(100),restart);
                });
            });
        }));
    }

    public int bottomInset() { return 90; }
    public void render(float delta) { root.update(delta);root.render(); }
    public void resize(int width,int height) { root.resize(width,height); }
    @Override
    public boolean isDisposed() { return root.isDisposed(); }
    @Override
    public void dispose() { root.dispose(); }
}
