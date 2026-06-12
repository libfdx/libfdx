package io.github.libfdx.ui;

/**
 * Represents an ui drop.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public final class UiDrop<T> {
    private final String acceptedType;
    private final UiDropHandler<T> handler;

    private UiDrop(String acceptedType, UiDropHandler<T> handler) {
        this.acceptedType = acceptedType;
        this.handler = handler;
    }

    /**
     * Runs the accept step.
     *
     * @param <T> the value type
     * @param acceptedType the accepted type
     * @param handler the handler
     * @return the accept
     */
    public static <T> UiDrop<T> accept(String acceptedType, UiDropHandler<T> handler) {
        return new UiDrop<T>(acceptedType, handler);
    }

    /**
     * Returns the accepted type.
     *
     * @return the accepted type
     */
    public String acceptedType() {
        return acceptedType;
    }

    /**
     * Runs the drop step.
     *
     * @param value the value
     * @return true if drop succeeds or is active; false otherwise
     */
    public boolean drop(T value) {
        return handler != null && handler.drop(value);
    }
}
