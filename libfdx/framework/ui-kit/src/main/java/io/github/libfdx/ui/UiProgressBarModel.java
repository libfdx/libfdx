package io.github.libfdx.ui;

/**
 * Represents an ui progress bar model.
 *
 * @author xpenatan
 */
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

    boolean matches(UiFloatState state, float minimum, float maximum) {
        return this.state == state
                && Float.compare(range.minimum(), minimum) == 0
                && Float.compare(range.maximum(), Math.max(minimum, maximum)) == 0;
    }
}
