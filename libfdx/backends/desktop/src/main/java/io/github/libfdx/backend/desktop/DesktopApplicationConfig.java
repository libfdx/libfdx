package io.github.libfdx.backend.desktop;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class DesktopApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig();
    private GraphicsAttachmentProvider graphics;

    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    public DesktopApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig();
        return this;
    }

    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    public DesktopApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    public DesktopApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    public DesktopApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    public DesktopApplicationConfig resizable(boolean resizable) {
        displayConfig.resizable(resizable);
        return this;
    }

    public DesktopApplicationConfig visible(boolean visible) {
        displayConfig.visible(visible);
        return this;
    }

    public DesktopApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    public DesktopApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
