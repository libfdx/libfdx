package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class DesktopNativeApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig();
    private GraphicsAttachmentProvider graphics;

    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    public DesktopNativeApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig();
        return this;
    }

    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    public DesktopNativeApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    public DesktopNativeApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    public DesktopNativeApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    public DesktopNativeApplicationConfig resizable(boolean resizable) {
        displayConfig.resizable(resizable);
        return this;
    }

    public DesktopNativeApplicationConfig visible(boolean visible) {
        displayConfig.visible(visible);
        return this;
    }

    public DesktopNativeApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    public DesktopNativeApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
