package io.github.libfdx.ui;

/**
 * Retained state for one radio-button choice.
 */
final class UiRadioModel {
    private UiIntState state;
    private int value;

    UiRadioModel(UiIntState state, int value) {
        update(state, value);
    }

    void update(UiIntState state, int value) {
        this.state = state;
        this.value = value;
    }

    boolean selected() {
        return state != null && state.get() == value;
    }

    void select() {
        if (state != null) {
            state.set(value);
        }
    }

    boolean sameGroup(UiRadioModel other) {
        return other != null && state != null && state == other.state;
    }
}
