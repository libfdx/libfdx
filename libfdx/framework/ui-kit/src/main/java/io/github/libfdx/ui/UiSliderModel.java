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

    boolean matches(UiFloatState state, float minimum, float maximum) {
        return this.state == state
                && range != null
                && Float.compare(range.minimum(), minimum) == 0
                && Float.compare(range.maximum(), Math.max(minimum, maximum)) == 0;
    }
}
