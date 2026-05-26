package io.github.libfdx.ui;

public final class UiDrop<T> {
    private final String acceptedType;
    private final UiDropHandler<T> handler;

    private UiDrop(String acceptedType, UiDropHandler<T> handler) {
        this.acceptedType = acceptedType;
        this.handler = handler;
    }

    public static <T> UiDrop<T> accept(String acceptedType, UiDropHandler<T> handler) {
        return new UiDrop<T>(acceptedType, handler);
    }

    public String acceptedType() {
        return acceptedType;
    }

    public boolean drop(T value) {
        return handler != null && handler.drop(value);
    }
}
