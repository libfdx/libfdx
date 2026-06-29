package io.github.libfdx.ui;

/**
 * Defines the contract for ui drop handler implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface UiDropHandler<T> {
    /**
     * Runs the drop step.
     *
     * @param value the value
     * @return true if drop succeeds or is active; false otherwise
     */
    boolean drop(T value);
}
