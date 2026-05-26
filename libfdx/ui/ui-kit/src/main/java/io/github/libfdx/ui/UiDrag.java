package io.github.libfdx.ui;

public final class UiDrag<T> {
    private final String type;
    private final T value;

    private UiDrag(String type, T value) {
        this.type = type;
        this.value = value;
    }

    public static <T> UiDrag<T> of(String type, T value) {
        return new UiDrag<T>(type, value);
    }

    public String type() {
        return type;
    }

    public T value() {
        return value;
    }
}
