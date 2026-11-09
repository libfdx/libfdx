package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.checkboxLabel;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiWindowState;
import java.util.function.Supplier;

/** Demonstrates floating, movable, and resizable windows. */
public final class UiKitWindowsSection implements UiContent {
    private static final float TOOLS_INITIAL_X = 0.0f;
    private static final float TOOLS_INITIAL_Y = 430.0f;
    private static final float STATS_INITIAL_X = 575.0f;
    private static final float STATS_INITIAL_Y = 430.0f;
    private static final float STATS_INITIAL_WIDTH = 270.0f;
    private static final float STATS_INITIAL_HEIGHT = 170.0f;

    private final UiBooleanState checked;
    private final UiBooleanState showDetails;
    private final Runnable incrementClick;
    private final Supplier<String> statusText;
    public final UiWindowState toolsWindow;
    public final UiWindowState statsWindow;

    public UiKitWindowsSection(UiBooleanState checked, UiBooleanState showDetails, Runnable incrementClick,
            Supplier<String> statusText, boolean compactWindows) {
        this.checked = checked;
        this.showDetails = showDetails;
        this.incrementClick = incrementClick;
        this.statusText = statusText;
        float toolsInitialX = compactWindows ? 0.0f : TOOLS_INITIAL_X;
        float toolsInitialY = compactWindows ? 0.0f : TOOLS_INITIAL_Y;
        float toolsInitialWidth = compactWindows ? 240.0f : 260.0f;
        float toolsInitialHeight = compactWindows ? 170.0f : 190.0f;
        float statsInitialX = compactWindows ? 80.0f : STATS_INITIAL_X;
        float statsInitialY = compactWindows ? 80.0f : STATS_INITIAL_Y;
        float statsInitialWidth = compactWindows ? 240.0f : STATS_INITIAL_WIDTH;
        float statsInitialHeight = compactWindows ? 170.0f : STATS_INITIAL_HEIGHT;
        toolsWindow = new UiWindowState(toolsInitialX, toolsInitialY, toolsInitialWidth, toolsInitialHeight)
                .minSize(240.0f, 170.0f);
        statsWindow = new UiWindowState(statsInitialX, statsInitialY, statsInitialWidth, statsInitialHeight)
                .minSize(240.0f, 170.0f);
    }

    @Override
    public void build(UiScope content) {
        content.text("Windows", Ui.modifier().style("title"));
        content.text("Floating layer", Ui.modifier().style("muted"));
    }

    public void buildWindows(UiScope page, float drawnVolume) {
        buildToolsWindow(page);
        buildStatsWindow(page, drawnVolume);
    }

    private void buildToolsWindow(UiScope page) {
        page.window("Tools Window", toolsWindow, tools -> {
            tools.text("drag the title bar", Ui.modifier().style("muted"));
            checkboxLabel(tools, "Window option", checked);
            tools.row(Ui.modifier().gap(8.0f), row -> {
                row.button("Press", incrementClick);
                row.button("Hide", () -> showDetails.set(false));
            });
        });
    }

    private void buildStatsWindow(UiScope page, float drawnVolume) {
        page.window("Resizable Stats", statsWindow, stats -> {
            stats.text("resize from the corner", Ui.modifier().style("muted"));
            stats.text(statusText.get(), Ui.modifier().style("small"));
            stats.custom("stats-meter", Ui.modifier().fillWidth().height(48.0f), custom -> {
                custom.draw((draw, bounds) -> {
                    UiRect track = new UiRect(bounds.x() + 8.0f, bounds.y() + 18.0f,
                            Math.max(1.0f, bounds.width() - 16.0f), 12.0f);
                    draw.rect(bounds, UiColor.rgba8888(0x101820f2));
                    draw.rect(track, UiColor.rgba8888(0x2b3442ff));
                    draw.rect(new UiRect(track.x(), track.y(), track.width() * drawnVolume, track.height()),
                            UiColor.rgba8888(0x7bd88fff));
                });
            });
        });
    }

}
