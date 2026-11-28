package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.samples.g2d.platformer.*;
import io.github.libfdx.samples.g2d.platformer.input.*;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.*;
import static io.github.libfdx.samples.g2d.platformer.input.PlatformerMenuInput.*;

/** Responsive field guide and touch controls in display units, independent of the world camera. */
public final class PlatformerHud {
    public enum Screen { TITLE, OPTIONS, HELP, LOADING, PLAYING, PAUSED, LOST, ARRIVING, CLEAR, FAILED }

    public final UiState<Screen> screen = Ui.state(Screen.TITLE);
    public Screen menuParent = Screen.TITLE;
    public final UiState<TextureRegion> panorama = Ui.state(null), emblem = Ui.state(null);
    public final UiState<Trail> trail = Ui.state(Trail.RAVINE);
    public final UiBooleanState canReplay = Ui.state(false), audioAvailable = Ui.state(false);
    public final UiIntState volume = Ui.state(7);

    private final UiState<String> tally = Ui.state(""), discovery = Ui.state("");
    private final UiState<ClockworkGate> chamber = Ui.state(null);
    private final UiState<String> readout = Ui.state("");
    private final UiIntState nearby = Ui.state(-1);
    private static final String[] NUMERALS = {"I", "II", "III"};
    private static final String[] LINKS = {"I + II", "II + III", "III"};
    private int lastReading = -1;
    private int coins = -1, visited = -1;
    private final UiIntState health = Ui.state(PlatformerGame.MAX_HEALTH);

    private PlatformerGame displayed;
    private float width, height;

    public void layout(UiRoot root, PlatformerView view) {
        float density = view.uiScale();
        float w = view.frameWidth() / density, h = view.frameHeight() / density;
        if (w == width && h == height && density == root.uiScale()) return;
        if (width == 0) theme(root);
        root.uiScale(density);
        width = w;
        height = h;
        root.requestCompose();
    }

    private static UiStyle fill(int rgba) {
        return UiStyle.style().padding(0).background(UiDrawable.color(UiColor.rgba8888(rgba)));
    }

    private static UiTextStyle type(float size, int rgba) {
        return UiTextStyle.text().font(UiFonts.defaultFont(size >= 40 ? 48 : size >= 20 ? 32 : 16))
                .size(size).color(UiColor.rgba8888(rgba)).wrap(false);
    }

    private static UiStyle centeredType(float size, int ink) {
        return UiStyle.style().text(type(size, ink).align(UiTextAlign.CENTER));
    }

    private static UiStyle button(int color, int hover, int ink) {
        UiStyle base = fill(color).text(type(15, ink).align(UiTextAlign.CENTER));
        UiStyle active = base.background(UiDrawable.color(UiColor.rgba8888(hover)));
        return base.hover(active).focused(active).pressed(active);
    }

    private static void theme(UiRoot root) {
        root.theme(Ui.darkTheme().text(UiStyle.style().text(type(15, 0xece8d6ff)))
                .panel(fill(0x152c35f5)).button(button(0x30484eff, 0x426269ff, 0xf5efdcff))
                .progressBar(fill(0x30484eff).foreground(UiDrawable.color(UiColor.rgba8888(0xeabb73ff))))
                .style("primary", button(0xeabb73ff, 0xffd39aff, 0x262c31ff))
                .style("control", button(0x172c32ce, 0x284952ed, 0xfffcf0ff))
                .style("wordmark", UiStyle.style().text(type(54, 0xffedc3ff).align(UiTextAlign.CENTER)
                        .shadow(UiColor.rgba8888(0x112324ff), 2).outline(UiColor.rgba8888(0x172e30ff), 1)))
                .style("heading", centeredType(29, 0xffefd0ff))
                .style("arrival", centeredType(26, 0xffefd0ff))
                .style("detail", centeredType(15, 0xccded6ff))
                .style("trail", centeredType(13, 0xf3c777ff))
                .style("shade", fill(0x07191f69)).style("frame", fill(0xa78552ff))
                .style("paper", fill(0x122b30fa)).style("inset", fill(0x213e42ff))
                .style("caption", UiStyle.style().text(type(13, 0xb8d2cbff)))
                .style("rule", fill(0xdca95eff)).style("hud", fill(0x122830d9))
                .style("energy", fill(0xffbf54ff)).style("empty", fill(0x576369ff))
                .style("ivory", UiStyle.style().text(type(15, 0xfffcf0ff)))
                .style("gold", UiStyle.style().text(type(13, 0xffda9aff))));
    }

    public void build(UiScope ui, PlatformerMenuInput menu, BackendPlatformerInput controls) {
        ui.stack(Ui.modifier().size(width, height).clip(), page -> {
            Screen current = screen.get();
            if (current != Screen.PLAYING && current != Screen.ARRIVING) {
                boolean title = current == Screen.TITLE || ((current == Screen.HELP || current == Screen.OPTIONS) && menuParent == Screen.TITLE);
                if (title || ((current == Screen.LOADING || current == Screen.FAILED) && !canReplay.get())) {
                    TextureRegion sky = panorama.get();
                    if (sky != null && width > 0 && height > 0) {
                        float cover = Math.max(width / sky.width(), height / sky.height());
                        int w = Math.max(1, Math.min(sky.width(), Math.round(width / cover)));
                        int h = Math.max(1, Math.min(sky.height(), Math.round(height / cover)));
                        TextureRegion crop = new TextureRegion(sky.texture(), (sky.width() - w) / 2, (sky.height() - h) / 2, w, h);
                        page.image(crop, at(0, 0, width, height));
                    }
                }
                page.panel(at(0, 0, width, height).style("shade"), null);
            }
            switch (current) {
                case TITLE -> title(page, menu);
                case OPTIONS -> options(page, menu);
                case HELP -> help(page, menu);
                case PLAYING -> play(page, menu, controls);
                case PAUSED -> pause(page, menu);
                case CLEAR, LOST -> outcome(page, menu);
                case LOADING, FAILED -> loading(page, menu);
                case ARRIVING -> { }
            }
        });
    }

    private void play(UiScope page, PlatformerMenuInput menu, BackendPlatformerInput controls) {
        page.panel(at(12, 12, 212, 64).style("hud"), null);
        label(page, trail.get().levelTitle, 24, 17, 195, 23, "ivory");
        label(page, tally.get(), 24, 41, 195, 18, "gold");
        for (int i = 0; i < PlatformerGame.MAX_HEALTH; i++) {
            page.panel(at(24 + 26 * i, 66, 22, 4).style(i < health.get() ? "energy" : "empty"), null);
        }
        ClockworkGate gate = chamber.get();
        if (gate != null && gate.solved()) {
            page.panel(at(12, 79, 212, 28).style("hud"), null);
            label(page, readout.get(), 24, 82, 195, 22, "gold");
        } else if (gate != null) {
            page.panel(at(12, 79, 254, 70).style("hud"), null);
            label(page, readout.get(), 24, 82, 234, 21, "gold");
            label(page, gate.clue(), 24, 104, 234, 19, "ivory");
            int dial = nearby.get();
            String hint = "Find a numbered stand.";
            if (dial >= 0) {
                String action = menu.touchControls.get() ? "Turn" : "Down";
                hint = gate.bells() ? action + " · Ring bell " + NUMERALS[dial]
                        : action + " · Switch " + NUMERALS[dial] + " changes " + LINKS[dial];
            }
            label(page, hint, 24, 126, 234, 19, "caption");
        } else if (!discovery.get().isEmpty()) {
            page.panel(at(12, 79, 212, 28).style("hud"), null);
            label(page, "Checkpoint · " + discovery.get(), 24, 82, 195, 22, "ivory");
        }
        page.button("Pause", at(width - 80, 12, 68, 34).style("control"), () -> menu.command(PlatformerMenuInput.PAUSE));
        if (menu.touchControls.get()) {
            page.stack(at(0, height - 65, width, 56), pad -> {
                pad.holdButton("<", at(12, 0, 54, 52).style("control").semanticLabel("Move left"), controls.leftButton);
                pad.holdButton(">", at(72, 0, 54, 52).style("control").semanticLabel("Move right"), controls.rightButton);
                pad.holdButton(nearby.get() >= 0 ? "Turn" : "Drop", at(width - 153, 0, 61, 52).style("control"), controls.dropButton);
                pad.holdButton("Jump", at(width - 86, 0, 74, 52).style("control"), controls.jumpButton);
            });
        }
    }

    private float menuWidth() {
        return Math.min(360, width - 24);
    }

    private void card(UiScope page, float h, UiContent content) {
        float w = menuWidth();
        page.stack(at((width - w) / 2, (height - h) / 2, w, h), box -> {
            box.panel(at(0, 0, w, h).style("frame"), null);
            box.panel(at(1, 1, w - 2, h - 2).style("paper"), null);
            content.build(box);
        });
    }

    private void title(UiScope page, PlatformerMenuInput menu) {
        float w = menuWidth();
        page.stack(at((width - w) / 2, (height - 282) / 2, w, 282), box -> {
            float middle = w / 2;
            box.panel(at(middle - 106, 30, 76, 2).style("rule"), null);
            box.panel(at(middle + 30, 30, 76, 2).style("rule"), null);
            TextureRegion gear = emblem.get();
            if (gear != null) box.image(gear, at(middle - 22, 8, 44, 46));
            label(box, "RIDGELINE", 0, 55, w, 63, "wordmark");
            box.panel(at(20, 124, w - 40, 139).style("paper"), null);
            menuAction(box, "Play", 128, menu, CONFIRM, true);
            menuAction(box, "Options", 174, menu, OPTIONS, false);
            menuAction(box, "How to play", 220, menu, HELP, false);
        });
    }

    private void menuAction(UiScope box, String text, float y, PlatformerMenuInput menu, int command, boolean primary) {
        action(box, text, 24, y, menuWidth() - 48, 39, menu, command, primary);
    }

    private void pause(UiScope page, PlatformerMenuInput menu) {
        float w = menuWidth();
        card(page, 300, box -> {
            label(box, trail.get().levelTitle, 8, 12, w - 16, 20, "trail");
            label(box, "Paused", 8, 33, w - 16, 38, "heading");
            label(box, trail.get().objective, 8, 72, w - 16, 22, "detail");
            menuAction(box, "Resume", 105, menu, CONFIRM, true);
            menuAction(box, "Options", 151, menu, OPTIONS, false);
            menuAction(box, "Restart level", 197, menu, RESTART, false);
            menuAction(box, "Main menu", 243, menu, MAIN_MENU, false);
        });
    }

    private void options(UiScope page, PlatformerMenuInput menu) {
        float w = menuWidth();
        card(page, 258, box -> {
            label(box, "Options", 0, 16, w, 42, "heading");
            if (audioAvailable.get()) {
                int value = volume.get();
                label(box, "Master volume   " + value * 10 + "%", 24, 69, w - 48, 24, "detail");
                action(box, "-", 24, 100, 44, 38, menu, QUIETER, false);
                box.progressBar(at(80, 116, w - 160, 6), value, 0, 10);
                action(box, "+", w - 68, 100, 44, 38, menu, LOUDER, false);
                menuAction(box, value == 0 ? "Unmute" : "Mute", 148, menu, MUTE, false);
            } else {
                label(box, "Audio unavailable", 24, 115, w - 48, 26, "detail");
            }
            menuAction(box, "Back", 197, menu, BACK, true);
        });
    }

    private void help(UiScope page, PlatformerMenuInput menu) {
        float w = menuWidth();
        card(page, 280, box -> {
            label(box, "How to play", 0, 16, w, 42, "heading");
            boolean touch = menu.touchControls.get();
            binding(box, 74, "Move", touch ? "Hold the arrows" : "A / D or Left / Right");
            binding(box, 106, "Jump or stomp", touch ? "Jump" : "Space, W or Up");
            binding(box, 138, "Drop or use dial", touch ? "Drop / Turn" : "S or Down");
            binding(box, 170, "Pause", touch ? "Pause" : "Esc or P");
            menuAction(box, "Back", 219, menu, BACK, true);
        });
    }

    private void binding(UiScope box, int y, String action, String control) {
        float w = menuWidth(), mid = w / 2 - 16;
        box.panel(at(24, y + 28, w - 48, 1).style("inset"), null);
        label(box, action, 28, y, mid - 28, 26, "ivory");
        label(box, control, mid, y, w - mid - 24, 26, "gold");
    }

    private void outcome(UiScope page, PlatformerMenuInput menu) {
        boolean clear = screen.get() == Screen.CLEAR;
        Trail current = trail.get();
        boolean checkpoint = !discovery.get().isEmpty() || (displayed != null && displayed.gate() != null && displayed.gate().solved());
        boolean replay = clear || checkpoint;
        float w = menuWidth();
        card(page, replay ? 276 : 230, box -> {
            String caption = clear ? current.title + (current.isLast() ? "  ·  Journey complete" : "  ·  Level complete") : current.levelTitle;
            label(box, caption, 8, 18, w - 16, 21, "trail");
            label(box, clear ? current.arrival : "Lost on the trail", 8, 41, w - 16, 42, "arrival");
            label(box, tally.get(), 8, 87, w - 16, 23, "detail");
            String primary = clear ? (current.isLast() ? "Play again" : "Next level") : (checkpoint ? "From checkpoint" : "Try again");
            menuAction(box, primary, 122, menu, CONFIRM, true);
            if (replay) menuAction(box, "Restart level", 168, menu, RESTART, false);
            menuAction(box, "Main menu", replay ? 214 : 168, menu, MAIN_MENU, false);
        });
    }

    private void loading(UiScope page, PlatformerMenuInput menu) {
        boolean failed = screen.get() == Screen.FAILED;
        float w = menuWidth();
        if (!failed) {
            card(page, 84, box -> label(box, "Opening trail…", 0, 20, w, 44, "heading"));
            return;
        }
        boolean replay = canReplay.get();
        card(page, replay ? 232 : 186, box -> {
            label(box, "Trail unavailable", 0, 16, w, 42, "heading");
            menuAction(box, "Retry", 72, menu, CONFIRM, true);
            if (replay) menuAction(box, "Replay level", 118, menu, PAUSE, false);
            menuAction(box, "Main menu", replay ? 164 : 118, menu, MAIN_MENU, false);
        });
    }

    private static void label(UiScope page, String text, float x, float y, float w, float h, String style) {
        UiModifier bounds = at(x, y, w, h);
        page.text(text, style == null ? bounds : bounds.style(style));
    }

    private static void action(UiScope page, String text, float x, float y, float w, float h, PlatformerMenuInput menu, int command, boolean primary) {
        UiModifier bounds = at(x, y, w, h);
        page.button(text, primary ? bounds.style("primary") : bounds, () -> menu.command(command));
    }

    private static UiModifier at(float x, float y, float w, float h) {
        return Ui.modifier().offset(x, y).size(w, h);
    }

    public void game(PlatformerGame game) {
        if (game == null) {
            displayed = null;
            chamber.set(null);
            return;
        }
        health.set(game.health());
        ClockworkGate gate = game.gate();
        if (gate != null && !gate.inChamber(game.player().x())) gate = null;
        if (chamber.get() != gate) lastReading = -1;
        chamber.set(gate);
        nearby.set(gate == null ? -1 : game.nearbyDial());
        if (gate != null) {
            int reading = gate.bells() ? gate.rung() : gate.lamps();
            if (gate.mistake()) reading += 1000;
            if (gate.solved()) reading = 2000;
            if (reading != lastReading) {
                lastReading = reading;
                if (gate.solved()) readout.set("Gate open · Checkpoint lit");
                else if (gate.mistake()) readout.set("Wrong order · Start again");
                else if (gate.bells()) readout.set("Bell order   " + bellHistory(gate.rung()));
                else readout.set("Lamps    I " + onOff(gate.lit(0)) + "    II " + onOff(gate.lit(1))
                            + "    III " + onOff(gate.lit(2)));
            }
        }
        if (game != displayed) {
            displayed = game;
            coins = visited = -1;
        }
        if (game.coinsCollected() != coins || game.visitedCount() != visited) {
            coins = game.coinsCollected();
            visited = game.visitedCount();
            tally.set(coins + "/" + game.coinTotal() + " gears   ·   " + visited + "/" + game.siteCount() + " places");
            discovery.set(game.lastSite() < 0 ? "" : game.siteName(game.lastSite()));
        }
    }

    private static String onOff(boolean lit) {
        return lit ? "on" : "off";
    }

    private static String bellHistory(int rung) {
        if (rung == 0) return "–   –   –";
        if (rung < 10) return NUMERALS[rung - 1] + "   –   –";
        return NUMERALS[rung / 10 - 1] + "   " + NUMERALS[rung % 10 - 1] + "   –";
    }

}
