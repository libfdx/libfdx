package io.github.libfdx.ui;

import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;

public final class UiToolkit {
    private final FileSystem files;
    private UiTheme theme = UiTheme.dark();

    public UiToolkit(FileSystem files) {
        this.files = files;
    }

    public UiRoot root(Display display, GraphicsContext graphics) {
        return new UiRoot(files, display, graphics, theme);
    }

    public FileSystem files() {
        return files;
    }

    public UiTheme theme() {
        return theme;
    }

    public UiToolkit theme(UiTheme theme) {
        this.theme = theme != null ? theme : UiTheme.dark();
        return this;
    }
}
