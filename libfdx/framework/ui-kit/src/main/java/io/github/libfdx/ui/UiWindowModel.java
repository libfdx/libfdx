package io.github.libfdx.ui;

/**
 * Represents an ui window model.
 *
 * @author xpenatan
 */
final class UiWindowModel {
    private final UiWindowState state;
    private final boolean defaultState;
    private UiRect layoutArea = UiRect.ZERO;

    UiWindowModel(UiWindowState state) {
        defaultState = state == null;
        this.state = state != null ? state : new UiWindowState(24.0f, 24.0f, 320.0f, 220.0f);
    }

    UiWindowState state() {
        return state;
    }

    boolean matches(UiWindowState state) {
        return state != null ? this.state == state : defaultState;
    }

    UiRect layoutArea() {
        return layoutArea;
    }

    void layoutArea(UiRect layoutArea) {
        this.layoutArea = layoutArea != null ? layoutArea : UiRect.ZERO;
    }
}
