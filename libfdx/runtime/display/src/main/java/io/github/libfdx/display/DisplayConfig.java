package io.github.libfdx.display;

public final class DisplayConfig {
    private String title = "libfdx";
    private int width = 800;
    private int height = 600;
    private boolean resizable = true;
    private boolean visible = true;
    private boolean vSync = true;
    private int foregroundFps = 60;

    public String title() {
        return title;
    }

    public DisplayConfig title(String title) {
        this.title = title;
        return this;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public DisplayConfig size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public boolean resizable() {
        return resizable;
    }

    public DisplayConfig resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public DisplayConfig visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean vSync() {
        return vSync;
    }

    public DisplayConfig vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    public int foregroundFps() {
        return foregroundFps;
    }

    public DisplayConfig foregroundFps(int foregroundFps) {
        this.foregroundFps = foregroundFps;
        return this;
    }
}
