package io.github.libfdx.ui;

public final class UiDoubleState extends UiObservableState {
    private double value;

    UiDoubleState(double value) {
        this.value = value;
    }

    public double get() {
        observeRead();
        return value;
    }

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
