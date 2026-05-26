package io.github.libfdx.ui;

public final class UiState<T> extends UiObservableState {
    private T value;

    UiState(T value) {
        rejectPrimitiveWrapper(value);
        this.value = value;
    }

    public T get() {
        observeRead();
        return value;
    }

    public void set(T value) {
        rejectPrimitiveWrapper(value);
        if (this.value == value || (this.value != null && this.value.equals(value))) {
            return;
        }
        this.value = value;
        notifyListeners();
    }

    private void rejectPrimitiveWrapper(T value) {
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof Character) {
            throw new IllegalArgumentException("Use dedicated primitive UI state instead of UiState<"
                    + value.getClass().getSimpleName() + ">");
        }
    }
}
