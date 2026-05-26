package io.github.libfdx.backend.psp;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;

public final class PspApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = fixedDisplayConfig(new DisplayConfig()
            .size(PspGraphicsContext.SCREEN_WIDTH, PspGraphicsContext.SCREEN_HEIGHT)
            .resizable(false));

    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    public PspApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = fixedDisplayConfig(displayConfig);
        return this;
    }

    public PspApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    public PspApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    public PspApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }

    private static DisplayConfig fixedDisplayConfig(DisplayConfig source) {
        DisplayConfig actual = source != null ? source : new DisplayConfig();
        DisplayConfig fixed = new DisplayConfig()
                .title(actual.title())
                .size(PspGraphicsContext.SCREEN_WIDTH, PspGraphicsContext.SCREEN_HEIGHT)
                .resizable(false)
                .visible(true)
                .vSync(actual.vSync())
                .foregroundFps(actual.foregroundFps());
        return fixed;
    }
}
