package io.github.libfdx.backend.android;

import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;

/**
 * Stores configuration values for an android application.
 *
 * @author xpenatan
 */
public final class AndroidApplicationConfig extends ApplicationConfig {
    private DisplayConfig displayConfig = new DisplayConfig().size(640, 480);
    private GraphicsAttachmentProvider graphics;
    private GraphicsAttachmentProvider[] fallbackGraphics = new GraphicsAttachmentProvider[0];
    private boolean graphicsFallbackEnabled;
    private String graphicsStartupRecoveryKey;
    private AndroidGraphicsFailureMode graphicsFailureMode = AndroidGraphicsFailureMode.SHOW_ERROR;
    private AndroidTextEditorStyle nativeTextEditorStyle = new AndroidTextEditorStyle();

    /**
     * Returns the display config.
     *
     * @return the display config
     */
    public DisplayConfig displayConfig() {
        return displayConfig;
    }

    /**
     * Sets the display config and returns this android application config.
     *
     * @param displayConfig the display config
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig displayConfig(DisplayConfig displayConfig) {
        this.displayConfig = displayConfig != null ? displayConfig : new DisplayConfig().size(640, 480);
        return this;
    }

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    public GraphicsAttachmentProvider graphics() {
        return graphics;
    }

    /**
     * Sets the graphics and returns this android application config.
     *
     * @param graphics the graphics context
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig graphics(GraphicsAttachmentProvider graphics) {
        this.graphics = graphics;
        graphicsProvider(graphics != null ? graphics.providerId() : null);
        return this;
    }

    /**
     * Returns the fallback graphics.
     *
     * @return the fallback graphics
     */
    public GraphicsAttachmentProvider[] fallbackGraphics() {
        return fallbackGraphics.clone();
    }

    /**
     * Sets ordered startup fallbacks and enables them when nonempty. Separate configurations of the same
     * provider are allowed. Reported initialization errors, including listener creation/resize and the first
     * frame, dispose the failed session before trying the next provider. The listener must support disposal
     * after a partial create and creation again on the replacement graphics device. Later rendering errors
     * do not restart the session. Fatal native crashes require a new process; see graphicsStartupRecoveryKey.
     *
     * @param fallbackGraphics the fallback graphics
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig fallbackGraphics(GraphicsAttachmentProvider... fallbackGraphics) {
        this.fallbackGraphics = copyProviders(fallbackGraphics);
        graphicsFallbackEnabled = this.fallbackGraphics.length > 0;
        return this;
    }

    /**
     * Returns the graphics fallback enabled.
     *
     * @return true if graphics fallback enabled succeeds or is active; false otherwise
     */
    public boolean graphicsFallbackEnabled() {
        return graphicsFallbackEnabled && fallbackGraphics.length > 0;
    }

    /** Returns the optional application-owned preferences key for startup recovery; null disables persistence. */
    public String graphicsStartupRecoveryKey() { return graphicsStartupRecoveryKey; }

    /**
     * Enables persisted recovery for this ordered graphics/fallback configuration. Use a new key when changing
     * the order or meaning of its attempts. App/OS updates reset the saved choice. Android 11+ native exit records
     * confirm startup crashes; ordinary termination and missing exit evidence never imply a crash.
     * A successful fallback is remembered after its first frame. Pass null to disable persistence.
     * Recovery covers initialization through the first frame, not subsequent device loss or crashes.
     * For example, an application using the optional WGPU provider can configure:
     * <pre>{@code
     * new AndroidApplicationConfig()
     *     .graphics(new WGPUProvider().backend(WGPUBackend.VULKAN))
     *     .fallbackGraphics(new WGPUProvider().backend(WGPUBackend.OPENGL_ES))
     *     .graphicsStartupRecoveryKey("wgpu-vulkan-gles-v1");
     * }</pre>
     * Exhausting the saved attempts reports startup failure until recovery is reset or the installation changes.
     * @see AndroidApplicationBackend#clearGraphicsStartupRecovery(android.content.Context, String)
     */
    public AndroidApplicationConfig graphicsStartupRecoveryKey(String key) {
        if(key != null) AndroidGraphicsStartupPreferences.name(key);
        graphicsStartupRecoveryKey = key;
        return this;
    }

    /**
     * Sets the graphics fallback enabled and returns this android application config.
     *
     * @param graphicsFallbackEnabled the graphics fallback enabled
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig graphicsFallbackEnabled(boolean graphicsFallbackEnabled) {
        this.graphicsFallbackEnabled = graphicsFallbackEnabled;
        return this;
    }

    /**
     * Returns the graphics failure mode.
     *
     * @return the graphics failure mode
     */
    public AndroidGraphicsFailureMode graphicsFailureMode() {
        return graphicsFailureMode;
    }

    /**
     * Sets the graphics failure mode and returns this android application config.
     *
     * @param graphicsFailureMode the graphics failure mode
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig graphicsFailureMode(AndroidGraphicsFailureMode graphicsFailureMode) {
        this.graphicsFailureMode = graphicsFailureMode != null ? graphicsFailureMode : AndroidGraphicsFailureMode.SHOW_ERROR;
        return this;
    }

    /**
     * Returns the native text editor style.
     *
     * @return the native text editor style
     */
    public AndroidTextEditorStyle nativeTextEditorStyle() {
        return nativeTextEditorStyle;
    }

    /**
     * Sets the native text editor style and returns this android application config.
     *
     * @param nativeTextEditorStyle the native text editor style
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig nativeTextEditorStyle(AndroidTextEditorStyle nativeTextEditorStyle) {
        this.nativeTextEditorStyle = nativeTextEditorStyle != null ? nativeTextEditorStyle : new AndroidTextEditorStyle();
        return this;
    }

    /**
     * Sets the title and returns this android application config.
     *
     * @param title the title
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig title(String title) {
        displayConfig.title(title);
        return this;
    }

    /**
     * Sets the size and returns this android application config.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig size(int width, int height) {
        displayConfig.size(width, height);
        return this;
    }

    /**
     * Sets the v sync and returns this android application config.
     *
     * @param vSync the v sync
     * @return this android application config for chaining
     */
    public AndroidApplicationConfig vSync(boolean vSync) {
        displayConfig.vSync(vSync);
        return this;
    }

    /**
     * Sets the foreground fps and returns this android application config.
     *
     * @param foregroundFps the foreground fps
     * @return this android application config for chaining
     */
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
