package io.github.libfdx.ui;

/**
 * Represents an ui window model.
 *
 * @author xpenatan
 */
final class UiWindowModel {
    private final UiWindowState state;
    private UiRect layoutArea = UiRect.ZERO;

    UiWindowModel(UiWindowState state) {
        this.state = state != null ? state : new UiWindowState(24.0f, 24.0f, 320.0f, 220.0f);
    }

    UiWindowState state() {
        return state;
    }

    UiRect layoutArea() {
        return layoutArea;
    }

    void layoutArea(UiRect layoutArea) {
        this.layoutArea = layoutArea != null ? layoutArea : UiRect.ZERO;
    }
}
