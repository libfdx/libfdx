package io.github.libfdx.ui;

public final class UiIntState extends UiObservableState {
    private int value;

    UiIntState(int value) {
        this.value = value;
    }

    public int get() {
        observeRead();
        return value;
    }

    public void set(int value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        notifyListeners();
    }
}
