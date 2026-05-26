package io.github.libfdx.backend.web;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class WebApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig().size(640, 480);
    private GraphicsAttachmentProvider graphics;
    private String canvasId = "libfdx-canvas";

    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    public WebApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig().size(640, 480);
        return this;
    }

    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    public WebApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    public String canvasId() {
        return canvasId;
    }

    public WebApplicationConfig canvasId(String canvasId) {
        this.canvasId = canvasId != null && canvasId.trim().length() > 0 ? canvasId : "libfdx-canvas";
        return this;
    }

    public WebApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    public WebApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    public WebApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    public WebApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }
}
