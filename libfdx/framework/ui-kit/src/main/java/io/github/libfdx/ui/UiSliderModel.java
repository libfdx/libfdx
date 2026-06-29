package io.github.libfdx.ui;

/**
 * Represents an ui slider model.
 *
 * @author xpenatan
 */
final class UiSliderModel {
    private final UiRange range;
    private final UiFloatState state;

    UiSliderModel(UiRange range, UiFloatState state) {
        this.range = range;
        this.state = state;
    }

    UiRange range() {
        return range;
    }

    UiFloatState state() {
        return state;
    }
}
