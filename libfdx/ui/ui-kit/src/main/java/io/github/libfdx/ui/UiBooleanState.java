package io.github.libfdx.ui;

/**
 * Represents an ui boolean state.
 *
 * @author xpenatan
 */
public final class UiBooleanState extends UiObservableState {
    private boolean value;

    UiBooleanState(boolean value) {
        this.value = value;
    }

    /**
     * Returns the get.
     *
     * @return true if get succeeds or is active; false otherwise
     */
    public boolean get() {
        observeRead();
        return value;
    }

    /**
     * Runs the set step.
     *
     * @param value the value
     */
    public void set(boolean value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }

    /**
     * Runs the toggle step.
     */
    public void toggle() {
        set(!value);
    }
}
