package io.github.libfdx.ui;

public final class UiLongState extends UiObservableState {
    private long value;

    UiLongState(long value) {
        this.value = value;
    }

    public long get() {
        observeRead();
        return value;
    }

    public void set(long value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }
}
