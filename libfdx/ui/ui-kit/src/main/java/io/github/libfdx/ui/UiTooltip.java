package io.github.libfdx.ui;

public final class UiTooltip {
    private final String text;
    private final int delayMillis;
    private final UiAlign align;

    private UiTooltip(String text, int delayMillis, UiAlign align) {
        this.text = text;
        this.delayMillis = Math.max(0, delayMillis);
        this.align = align != null ? align : UiAlign.CENTER;
    }

    public static UiTooltip text(String text) {
        return new UiTooltip(text, 350, UiAlign.CENTER);
    }

    public UiTooltip delayMillis(int delayMillis) {
        return new UiTooltip(text, delayMillis, align);
    }

    public UiTooltip align(UiAlign align) {
        return new UiTooltip(text, delayMillis, align);
    }

    public String text() {
        return text;
    }

    public int delayMillis() {
        return delayMillis;
    }

    public UiAlign align() {
        return align;
    }
}
