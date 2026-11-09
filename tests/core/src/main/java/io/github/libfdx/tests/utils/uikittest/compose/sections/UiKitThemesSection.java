package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcaseThemes;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;

/** Demonstrates switching the global palette. */
public final class UiKitThemesSection implements UiContent {
    private final UiIntState activeTheme;
    private final ThemeSelection themeSelection;

    public UiKitThemesSection(UiIntState activeTheme, ThemeSelection themeSelection) {
        this.activeTheme = activeTheme;
        this.themeSelection = themeSelection;
    }

    @Override
    public void build(UiScope content) {
        content.text("Themes", Ui.modifier().style("title"));
        content.text("Choose one global palette, then visit a widget section to inspect its themed states.",
                Ui.modifier().style("muted"));
        buildPaletteChoices(content);
        buildPaletteSummary(content);
    }

    private void buildPaletteChoices(UiScope content) {
        content.text("Available palettes", Ui.modifier().style("section"));
        content.grid(3, Ui.modifier().fillWidth().gap(8.0f), grid -> {
            for (int i = 0; i < UiKitShowcaseThemes.count(); i++) {
                final int themeIndex = i;
                UiModifier modifier = Ui.modifier().fillWidth();
                if (activeTheme.get() == themeIndex) {
                    modifier = modifier.style("nav-active");
                }
                if (themeIndex == 2) {
                    modifier = modifier.validationId(UiKitValidationIds.THEME_COBALT);
                }
                grid.button(UiKitShowcaseThemes.name(i), modifier, () -> themeSelection.select(themeIndex));
            }
        });
    }

    private void buildPaletteSummary(UiScope content) {
        content.panel(Ui.modifier().fillWidth().padding(12.0f).gap(5.0f).style("tile"), summary -> {
            summary.text("Active palette: " + UiKitShowcaseThemes.name(activeTheme.get()), Ui.modifier().style("section"));
            summary.text("The selected palette applies to every widget section and window.",
                    Ui.modifier().style("small"));
            summary.text("Palette selection persists while navigating between sections.",
                    Ui.modifier().style("small"));
        });
    }

    @FunctionalInterface
    public interface ThemeSelection {
        void select(int index);

    }

}
