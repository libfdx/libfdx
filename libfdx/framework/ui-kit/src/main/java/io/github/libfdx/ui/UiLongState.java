package io.github.libfdx.ui;

/**
 * Represents an ui long state.
 *
 * @author xpenatan
 */
public final class UiLongState extends UiObservableState {
    private long value;

    UiLongState(long value) {
        this.value = value;
    }

    /**
     * Returns the get.
     *
     * @return the get
     */
    public long get() {
        observeRead();
        return value;
    }

    /**
     * Runs the set step.
     *
     * @param value the value
     */
    public void set(long value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }
}
