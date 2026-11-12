package io.github.libfdx.tools.uipreview;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.ui.UiTheme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Desktop development configuration. Configure before calling {@link UiPreview}. */
public final class UiPreviewConfig {
    enum InputMode { FEEDBACK, LIVE, NONE }

    final int width;
    final int height;
    String title = "UI Preview";
    UiTheme theme = UiTheme.dark();
    float uiScale = 1;
    InputMode inputMode = InputMode.FEEDBACK;
    GraphicsAttachmentProvider graphics;
    final List<Path> assets = new ArrayList<>();
    Path capture;
    long exitAfterFrames;

    /** Width/height are positive desktop window client dimensions, before automatic display scaling. */
    public UiPreviewConfig(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive preview dimensions required");
        this.width = width;
        this.height = height;
    }

    /** Sets the window title. */
    public UiPreviewConfig title(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Preview title required");
        this.title = title;
        return this;
    }

    /** Selects the initial theme. */
    public UiPreviewConfig theme(UiTheme theme) {
        if (theme == null) throw new IllegalArgumentException("Preview theme required");
        this.theme = theme;
        return this;
    }

    /** Sets additional UI scale; display content scaling remains automatic. */
    public UiPreviewConfig uiScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Positive finite UI scale required");
        uiScale = scale;
        return this;
    }

    /**
     * Selects full UI input when true or a static view when false. Leave unset for the
     * default design preview, which shows pointer feedback without invoking input callbacks.
     * Preview host shortcuts remain available in all modes.
     *
     * @param interactive whether to enable normal UI actions
     * @return this configuration
     */
    public UiPreviewConfig interactive(boolean interactive) {
        inputMode = interactive ? InputMode.LIVE : InputMode.NONE;
        return this;
    }

    /** Supplies an explicit provider. Null selects the desktop OpenGL default. */
    public UiPreviewConfig graphics(GraphicsAttachmentProvider provider) { graphics = provider; return this; }

    /** Adds a prioritized internal asset directory. Directories remain borrowed. */
    public UiPreviewConfig assets(Path directory) {
        if (directory == null) throw new IllegalArgumentException("Asset directory required");
        assets.add(directory.toAbsolutePath().normalize());
        return this;
    }

    /** Captures the first rendered UI frame as PNG; F8 captures again to this path. */
    public UiPreviewConfig capture(Path file) { capture = file; return this; }

    /** Automatically exits after this many rendered UI frames; zero keeps the preview open. Useful for capture jobs. */
    public UiPreviewConfig exitAfterFrames(long count) {
        if (count < 0) throw new IllegalArgumentException("Frame count cannot be negative");
        exitAfterFrames = count;
        return this;
    }

    boolean sharesWindowWith(UiPreviewConfig other) {
        return width == other.width && height == other.height
                && Objects.equals(graphics, other.graphics) && assets.equals(other.assets)
                && Objects.equals(capture, other.capture) && exitAfterFrames == other.exitAfterFrames;
    }

}
