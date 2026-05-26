package io.github.libfdx.ui;

public final class UiBooleanState extends UiObservableState {
    private boolean value;

    UiBooleanState(boolean value) {
        this.value = value;
    }

    public boolean get() {
        observeRead();
        return value;
    }

    public void set(boolean value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }

    public void toggle() {
        set(!value);
    }
}
