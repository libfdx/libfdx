package io.github.libfdx.ui;

import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;

/**
 * Represents an ui toolkit.
 *
 * @author xpenatan
 */
public final class UiToolkit {
    private final FileSystem files;
    private UiTheme theme = UiTheme.dark();

    /**
     * Creates an UI toolkit.
     *
     * @param files the files
     */
    public UiToolkit(FileSystem files) {
        this.files = files;
    }

    /**
     * Runs the root step.
     *
     * @param display the display
     * @param graphics the graphics context
     * @return the root
     */
    public UiRoot root(Display display, GraphicsContext graphics) {
        return new UiRoot(files, display, graphics, theme);
    }

    /**
     * Returns the files.
     *
     * @return the files
     */
    public FileSystem files() {
        return files;
    }

    /**
     * Returns the theme.
     *
     * @return the theme
     */
    public UiTheme theme() {
        return theme;
    }

    /**
     * Sets the theme and returns this UI toolkit.
     *
     * @param theme the theme
     * @return this UI toolkit for chaining
     */
    public UiToolkit theme(UiTheme theme) {
        this.theme = theme != null ? theme : UiTheme.dark();
        return this;
    }
}
