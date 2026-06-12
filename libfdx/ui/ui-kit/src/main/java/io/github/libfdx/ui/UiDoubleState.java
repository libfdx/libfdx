package io.github.libfdx.ui;

/**
 * Represents an ui double state.
 *
 * @author xpenatan
 */
public final class UiDoubleState extends UiObservableState {
    private double value;

    UiDoubleState(double value) {
        this.value = value;
    }

    /**
     * Returns the get.
     *
     * @return the get
     */
    public double get() {
        observeRead();
        return value;
    }

    /**
     * Runs the set step.
     *
     * @param value the value
     */
    public void set(double value) {
        if (same(this.value, value)) {
            return;
        }
        this.value = value;
        notifyListeners();
    }

    private boolean same(double a, double b) {
        return a == b || (a != a && b != b);
    }
}
