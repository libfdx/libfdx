package io.github.libfdx.ui;

public final class UiModal {
    private final String id;
    private final UiColor scrimColor;
    private final boolean dismissOnEscape;

    private UiModal(String id, UiColor scrimColor, boolean dismissOnEscape) {
        this.id = id;
        this.scrimColor = scrimColor != null ? scrimColor : UiColor.rgba(0.0f, 0.0f, 0.0f, 0.5f);
        this.dismissOnEscape = dismissOnEscape;
    }

    public static UiModal modal(String id) {
        return new UiModal(id, UiColor.rgba(0.0f, 0.0f, 0.0f, 0.5f), true);
    }

    public UiModal scrim(UiColor scrimColor) {
        return new UiModal(id, scrimColor, dismissOnEscape);
    }

    public UiModal dismissOnEscape(boolean dismissOnEscape) {
        return new UiModal(id, scrimColor, dismissOnEscape);
    }

    public String id() {
        return id;
    }

    public UiColor scrimColor() {
        return scrimColor;
    }

    public boolean dismissOnEscape() {
        return dismissOnEscape;
    }
}
