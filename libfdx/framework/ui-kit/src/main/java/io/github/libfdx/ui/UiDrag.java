package io.github.libfdx.ui;

/**
 * Represents an ui drag.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public final class UiDrag<T> {
    private final String type;
    private final T value;

    private UiDrag(String type, T value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Creates a UI drag from the supplied values.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param value the value
     * @return the of
     */
    public static <T> UiDrag<T> of(String type, T value) {
        return new UiDrag<T>(type, value);
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public T value() {
        return value;
    }
}
