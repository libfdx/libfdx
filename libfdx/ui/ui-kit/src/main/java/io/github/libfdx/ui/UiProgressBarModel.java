package io.github.libfdx.ui;

final class UiProgressBarModel {
    private final UiRange range;
    private final UiFloatState state;

    UiProgressBarModel(UiRange range, UiFloatState state) {
        this.range = range != null ? range : new UiRange(0.0f, 1.0f);
        this.state = state;
    }

    UiRange range() {
        return range;
    }

    UiFloatState state() {
        return state;
    }
}
