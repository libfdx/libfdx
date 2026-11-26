package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.samples.g2d.platformer.PlatformerGame;
import io.github.libfdx.samples.g2d.platformer.PlatformerView;
import io.github.libfdx.samples.g2d.platformer.input.BackendPlatformerInput;
import io.github.libfdx.samples.g2d.platformer.input.PlatformerMenuInput;
import io.github.libfdx.ui.*;

/** Application-owned HUD. All positions use the same 300 by 180 logical scene as the map. */
public final class PlatformerHud {
    public final UiState<String> title = Ui.state("LIBFDX TRAILS"), score = Ui.state("COINS 0/0");
    public final UiState<String> status = Ui.state("LOADING LEVEL"), heading = Ui.state("LOADING");
    public final UiBooleanState menuOpen = Ui.state(false), loading = Ui.state(true), failed = Ui.state(false);
    public final UiBooleanState audioAvailable = Ui.state(false), audioLocked = Ui.state(false);
    public final UiIntState volume = Ui.state(7);
    private int lastCoins = -1, lastTotal = -1;
    private int revision = -1;
    private float scale = 1, originX, originY;

    private UiFont font;

    public void layout(UiRoot root, PlatformerView view, int frameHeight) {
        if (revision == view.revision()) return;
        revision = view.revision();
        var viewport = view.viewport();
        int density = Math.min(4, viewport.scale());
        root.uiScale(density);
        float nextScale = viewport.scale() / (float) density;
        if (scale != nextScale) {
            scale = nextScale;
            applyTheme(root);
        }
        originX = viewport.x() / (float) density;
        originY = (frameHeight - viewport.y() - PlatformerView.HEIGHT * viewport.scale()) / (float) density;
        root.requestCompose();
    }

    public void font(UiRoot root, UiFont font) {
        this.font = font;
        applyTheme(root);
    }

    private void applyTheme(UiRoot root) {
        if (font == null) return;
        var text = UiTextStyle.text().font(font).size(8 * scale).lineHeight(10 * scale).wrap(false);
        var button = UiStyle.button().padding(scale).text(text.align(UiTextAlign.CENTER))
                .background(UiDrawable.color(UiColor.rgba8888(0x254548ee)));
        root.theme(Ui.darkTheme().text(UiStyle.style().text(text))
                .button(button.pressed(button.background(UiDrawable.color(UiColor.rgba8888(0x587f66ff))))));
    }

    public void build(UiScope ui, PlatformerMenuInput menu, BackendPlatformerInput controls) {
        ui.stack(Ui.modifier().offset(originX, originY).size(300 * scale, 180 * scale), page -> {
            page.panel(at(6, 6, 288, 22), null);
            page.text(title.get(), at(12, 8, 140, 18));
            page.text(score.get(), at(160, 8, 92, 18));
            page.button("MENU", at(258, 8, 34, 16), () -> menu.command(PlatformerMenuInput.PAUSE));
            if (loading.get() || failed.get() || menuOpen.get()) {
                page.panel(at(47, 39, 206, loading.get() || failed.get() ? 102 : 123), null);
                page.text(heading.get(), at(62, 45, 176, 19));
                if (loading.get() || failed.get()) {
                    page.text(status.get(), at(62, 69, 176, 18));
                    page.text(failed.get() ? "R RETRY  P RETURN" : "PREPARING YOUR TRAIL", at(62, 96, 176, 18));
                } else {
                    page.button("RESUME", at(62, 70, 80, 18), () -> menu.command(PlatformerMenuInput.PAUSE));
                    page.button("RESTART", at(158, 70, 80, 18), () -> menu.command(PlatformerMenuInput.RESTART));
                    page.button("NEXT TRAIL", at(62, 94, 176, 18), () -> menu.command(PlatformerMenuInput.NEXT));
                    page.button("-", at(62, 116, 24, 18), () -> menu.command(PlatformerMenuInput.QUIETER));
                    page.progressBar(at(94, 121, 78, 8), volume.get(), 0, 10);
                    page.button("+", at(180, 116, 24, 18), () -> menu.command(PlatformerMenuInput.LOUDER));
                    page.button("M", at(212, 116, 26, 18), () -> menu.command(PlatformerMenuInput.MUTE));
                    page.text(audioAvailable.get() ? "VOLUME: Q/E   M MUTE" : "NO AUDIO PROVIDER", at(62, 143, 176, 16));
                }
            } else {
                page.holdButton("<", at(8, 144, 36, 28).semanticLabel("Move left"), controls.leftButton);
                page.holdButton(">", at(50, 144, 36, 28).semanticLabel("Move right"), controls.rightButton);
                page.holdButton("JUMP", at(242, 144, 50, 28), controls.jumpButton);
                page.panel(at(90, 150, 146, 22), null);
                page.text(audioLocked.get() ? "PRESS A KEY FOR SOUND" : audioAvailable.get()
                        ? "A/D MOVE  SPACE JUMP" : "SILENT MODE", at(94, 154, 140, 18));
            }
        });
    }

    private UiModifier at(float x, float y, float width, float height) {
        return Ui.modifier().offset(x * scale, y * scale).size(width * scale, height * scale);
    }

    public void game(PlatformerGame game) {
        if (game == null) return;
        int coins = game.coinsCollected(), total = game.coinTotal();
        if (coins != lastCoins || total != lastTotal) {
            lastCoins = coins;
            lastTotal = total;
            score.set("COINS " + coins + "/" + total);
        }
    }

}
