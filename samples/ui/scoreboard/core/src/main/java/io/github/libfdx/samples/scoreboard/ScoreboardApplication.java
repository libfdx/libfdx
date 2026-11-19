package io.github.libfdx.samples.scoreboard;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.storage.KeyValueStore;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiFonts;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;

/** A two-team scoreboard that restores the match when the application reopens. */
public final class ScoreboardApplication extends ApplicationAdapter {
    private final UiIntState home = Ui.state(0);
    private final UiIntState away = Ui.state(0);
    private final long exitAfterFrames;
    private Fdx fdx;
    private UiRoot root;
    private KeyValueStore match;
    private long frames;

    public ScoreboardApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        match = fdx.storage().local("scoreboard-match").load();
        home.set(match.getInt("home", 0));
        away.set(match.getInt("away", 0));
        root = new UiToolkit(fdx.files()).theme(scoreboardTheme())
                .root(fdx.displays().main(), fdx.graphics().main()).input(fdx.input());
        root.setContent(this::scoreboard);
    }

    private static UiTheme scoreboardTheme() {
        UiTheme theme = UiTheme.dark();
        UiTextStyle label = UiTextStyle.text().font(UiFonts.defaultFont(16)).size(16);
        UiTextStyle score = label.font(UiFonts.defaultFont(48)).size(48);
        return theme.style("text", theme.style("text").text(label))
                .style("button", theme.style("button").text(label))
                .style("title", UiStyle.style().text(label.font(UiFonts.defaultFont(26)).size(26)))
                .style("home-score", UiStyle.style().text(score.color(UiColor.rgba8888(0x80dce6ff))))
                .style("away-score", UiStyle.style().text(score.color(UiColor.rgba8888(0xffc58aff))));
    }

    private void scoreboard(UiScope ui) {
        ui.scroll(Ui.modifier().fill(), viewport ->
                viewport.column(Ui.modifier().fillWidth().maxWidth(640).align(UiAlign.CENTER)
                        .padding(20).gap(14), board -> {
                    board.text("Scoreboard", Ui.modifier().style("title"));
                    board.row(Ui.modifier().fillWidth().gap(12), teams -> {
                        team(teams, "Home", "home-score", home);
                        team(teams, "Away", "away-score", away);
                    });
                    board.button("New match", Ui.modifier().height(38)
                            .enabled(home.get() != 0 || away.get() != 0), () -> {
                        home.set(0);
                        away.set(0);
                        save();
                    });
                }));
    }

    private void team(UiScope teams, String name, String scoreStyle, UiIntState points) {
        teams.panel(Ui.modifier().fillWidth().padding(14).gap(8), card -> {
            card.text(name);
            card.text(Integer.toString(points.get()), Ui.modifier().style(scoreStyle));
            card.button("+1 point", Ui.modifier().fillWidth().height(38), () -> {
                points.set(points.get() + 1);
                save();
            });
        });
    }

    private void save() {
        match.putInt("home", home.get()).putInt("away", away.get()).flush();
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        fdx.graphics().main().clear(0.055f, 0.075f, 0.10f, 1);
        root.update(fdx.app().deltaTime());
        root.render();
        if (exitAfterFrames > 0 && ++frames >= exitAfterFrames) {
            fdx.app().requestExit();
        }
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
        }
    }

}
