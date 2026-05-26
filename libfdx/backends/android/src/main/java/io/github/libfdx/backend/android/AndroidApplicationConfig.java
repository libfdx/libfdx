package io.github.libfdx.backend.android;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

public final class AndroidApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig().size(640, 480);
    private GraphicsAttachmentProvider graphics;
    private GraphicsAttachmentProvider[] fallbackGraphics = new GraphicsAttachmentProvider[0];
    private boolean graphicsFallbackEnabled;
    private AndroidGraphicsFailureMode graphicsFailureMode = AndroidGraphicsFailureMode.SHOW_ERROR;
    private AndroidTextEditorStyle nativeTextEditorStyle = new AndroidTextEditorStyle();

    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    public AndroidApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig().size(640, 480);
        return this;
    }

    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    public AndroidApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    public GraphicsAttachmentProvider[] fallbackGraphics() {
        return fallbackGraphics.clone();
    }

    public AndroidApplicationConfig fallbackGraphics(GraphicsAttachmentProvider... fallbackGraphics) {
        this.fallbackGraphics = copyProviders(fallbackGraphics);
        graphicsFallbackEnabled = this.fallbackGraphics.length > 0;
        return this;
    }

    public boolean graphicsFallbackEnabled() {
        return graphicsFallbackEnabled && fallbackGraphics.length > 0;
    }

    public AndroidApplicationConfig graphicsFallbackEnabled(boolean graphicsFallbackEnabled) {
        this.graphicsFallbackEnabled = graphicsFallbackEnabled;
        return this;
    }

    public AndroidGraphicsFailureMode graphicsFailureMode() {
        return graphicsFailureMode;
    }

    public AndroidApplicationConfig graphicsFailureMode(AndroidGraphicsFailureMode graphicsFailureMode) {
        this.graphicsFailureMode = graphicsFailureMode != null ? graphicsFailureMode : AndroidGraphicsFailureMode.SHOW_ERROR;
        return this;
    }

    public AndroidTextEditorStyle nativeTextEditorStyle() {
        return nativeTextEditorStyle;
    }

    public AndroidApplicationConfig nativeTextEditorStyle(AndroidTextEditorStyle nativeTextEditorStyle) {
        this.nativeTextEditorStyle = nativeTextEditorStyle != null ? nativeTextEditorStyle : new AndroidTextEditorStyle();
        return this;
    }

    public AndroidApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    public AndroidApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    public AndroidApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    public AndroidApplicationConfig foregroundFps(int foregroundFps) {
        displayConfig.foregroundFps(foregroundFps);
        return this;
    }

    private static GraphicsAttachmentProvider[] copyProviders(GraphicsAttachmentProvider[] providers) {
        if (providers == null || providers.length == 0) {
            return new GraphicsAttachmentProvider[0];
        }
        int count = 0;
        for (GraphicsAttachmentProvider provider : providers) {
            if (provider != null) {
                count++;
            }
        }
        if (count == 0) {
            return new GraphicsAttachmentProvider[0];
        }
        GraphicsAttachmentProvider[] copy = new GraphicsAttachmentProvider[count];
        int index = 0;
        for (GraphicsAttachmentProvider provider : providers) {
            if (provider != null) {
                copy[index++] = provider;
            }
        }
        return copy;
    }
}
