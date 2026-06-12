package io.github.libfdx.backend.psp;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;

/**
 * Stores configuration values for a psp application.
 *
 * @author xpenatan
 */
public final class PspApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = fixedDisplayConfig(new DisplayConfig()
            .size(PspGraphicsContext.SCREEN_WIDTH, PspGraphicsContext.SCREEN_HEIGHT)
            .resizable(false));

    /**
     * Returns the display config.
     *
     * @return the display config
     */
    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    /**
     * Sets the display config and returns this PSP application config.
     *
     * @param displayConfig the display config
     * @return this PSP application config for chaining
     */
    public PspApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = fixedDisplayConfig(displayConfig);
        return this;
    }

    /**
     * Sets the title and returns this PSP application config.
     *
     * @param title the title
     * @return this PSP application config for chaining
     */
    public PspApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the v sync and returns this PSP application config.
     *
     * @param vSync the v sync
     * @return this PSP application config for chaining
     */
    public PspApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    /**
     * Sets the foreground fps and returns this PSP application config.
     *
     * @param foregroundFps the foreground fps
     * @return this PSP application config for chaining
     */
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
