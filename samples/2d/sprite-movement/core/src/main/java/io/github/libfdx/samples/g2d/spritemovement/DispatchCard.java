package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.*;

/** UI Kit renders the dispatch slip in display units, independently of the harbor camera. */
final class DispatchCard {
    private final UiIntState stop = Ui.state(-1);
    private final TextureRegion post;
    private final Runnable start;
    private float width, height;

    DispatchCard(TextureRegion post, Runnable start) {
        this.post = post;
        this.start = start;
    }

    void stop(int index) {
        stop.set(index);
    }

    void layout(UiRoot ui, Display display, int fw, int fh) {
        float fit = Math.min(display.width() / 344f, display.height() / 268f);
        float scale = Math.max(.25f, Math.min(1.35f, fit) * fw / Math.max(1f, display.width()));
        float w = fw / scale, h = fh / scale;
        if (w == width && h == height && scale == ui.uiScale()) return;
        width = w;
        height = h;
        ui.uiScale(scale);
        ui.requestCompose();
    }

    void build(UiScope ui) {
        int index = stop.get();
        ui.stack(Ui.modifier().size(width, height), page -> {
            if (index >= 0 && index < SpriteMovementState.ROUTE.length) {
                page.panel(at(12, 12, 181, 55).style("ink"), null);
                page.panel(at(12, 12, 4, 55).style("signal"), null);
                page.text("DELIVERY   " + (index + 1) + " / " + SpriteMovementState.ROUTE.length,
                        at(27, 17, 156, 18).style("overline"));
                page.text(SpriteMovementState.ROUTE[index].name(), at(26, 33, 161, 29).style("dock"));
                return;
            }

            boolean finished = index >= SpriteMovementState.ROUTE.length;
            page.panel(at(0, 0, width, height).style("scrim"), null);
            float w = Math.min(376, width - 28), h = 234;
            page.stack(at((width - w) / 2, (height - h) / 2, w, h), card -> {
                card.panel(at(3, 4, w - 3, h - 4).style("ink"), null);
                card.panel(at(0, 0, w - 3, h - 4).style("paper"), null);
                card.panel(at(0, 0, w - 3, 5).style("signal"), null);
                card.image(post, at(w - 67, 19, 40, 44));
                card.text("HARBOR MAIL", at(24, 23, w - 112, 21).style("postmark"));
                card.text(finished ? "Mail delivered" : "Tidepost", at(23, 46, w - 47, 53).style("title"));
                card.text(finished ? "All three docks received their mail." : "Deliver the mail to three harbor docks.",
                        at(25, 103, w - 47, 24).style("copy"));
                card.panel(at(25, 139, w - 53, 1).style("rule"), null);
                card.text(finished ? "A new route starts at the anchorage." : "WASD / arrows, left stick or drag to steer",
                        at(25, 146, w - 48, 23).style("controls"));
                card.button(finished ? "Sail again" : "Start route", at(24, 179, w - 52, 35), start);
            });
        });
    }

    static void theme(UiRoot root) {
        UiStyle button = fill(0xa23a29ff).text(text(16, 0xfff6ddff).align(UiTextAlign.CENTER));
        UiStyle highlight = fill(0x843022ff).text(button.textStyle());
        root.theme(Ui.darkTheme().button(button.hover(highlight).focused(highlight).pressed(highlight))
                .style("paper", fill(0xfff0d1ff)).style("ink", fill(0x173b40f5))
                .style("signal", fill(0xcc5036ff)).style("rule", fill(0xb3a58fff))
                .style("scrim", fill(0x0c2f3b55))
                .style("title", UiStyle.style().text(text(40, 0x193d42ff)))
                .style("postmark", UiStyle.style().text(text(12, 0x95402eff)))
                .style("copy", UiStyle.style().text(text(16, 0x233f42ff)))
                .style("controls", UiStyle.style().text(text(13, 0x3d5653ff)))
                .style("overline", UiStyle.style().text(text(11, 0xffce9dff)))
                .style("dock", UiStyle.style().text(text(19, 0xfff2d5ff))));
    }

    private static UiTextStyle text(float size, int ink) {
        return UiTextStyle.text().font(UiFonts.defaultFont(size)).size(size).color(UiColor.rgba8888(ink)).wrap(false);
    }

    private static UiStyle fill(int color) {
        return UiStyle.style().padding(0).background(UiDrawable.color(UiColor.rgba8888(color)));
    }

    private static UiModifier at(float x, float y, float width, float height) {
        return Ui.modifier().offset(x, y).size(width, height);
    }

}
