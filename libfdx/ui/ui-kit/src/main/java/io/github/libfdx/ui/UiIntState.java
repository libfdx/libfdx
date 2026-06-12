package io.github.libfdx.ui;

/**
 * Represents an ui int state.
 *
 * @author xpenatan
 */
public final class UiIntState extends UiObservableState {
    private int value;

    UiIntState(int value) {
        this.value = value;
    }

    /**
     * Returns the get.
     *
     * @return the get
     */
    public int get() {
        observeRead();
        return value;
    }

    /**
     * Runs the set step.
     *
     * @param value the value
     */
    public void set(int value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }
}
