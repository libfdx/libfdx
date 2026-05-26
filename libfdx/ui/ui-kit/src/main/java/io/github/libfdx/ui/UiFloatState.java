package io.github.libfdx.ui;

public final class UiFloatState extends UiObservableState {
    private float value;

    UiFloatState(float value) {
        this.value = value;
    }

    public float get() {
        observeRead();
        return value;
    }

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
