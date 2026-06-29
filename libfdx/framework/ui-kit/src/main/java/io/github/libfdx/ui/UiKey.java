package io.github.libfdx.ui;

/**
 * Defines the contract for ui key implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface UiKey<T> {
    /**
     * Runs the key step.
     *
     * @param item the item
     * @return the key
     */
    Object key(T item);
}
