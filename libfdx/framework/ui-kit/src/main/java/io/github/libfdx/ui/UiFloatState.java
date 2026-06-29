package io.github.libfdx.ui;

/**
 * Represents an ui float state.
 *
 * @author xpenatan
 */
public final class UiFloatState extends UiObservableState {
    private float value;

    UiFloatState(float value) {
        this.value = value;
    }

    /**
     * Returns the get.
     *
     * @return the get
     */
    public float get() {
        observeRead();
        return value;
    }

    /**
     * Runs the set step.
     *
     * @param value the value
     */
    public void set(float value) {
        if (same(this.value, value)) {
            return;
        }
        this.value = value;
        notifyListeners();
    }

    private boolean same(float a, float b) {
        return a == b || (a != a && b != b);
    }
}
