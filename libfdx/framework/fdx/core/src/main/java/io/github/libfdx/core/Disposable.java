package io.github.libfdx.core;

/**
 * Defines the contract for disposable implementations.
 *
 * @author xpenatan
 */
public interface Disposable {
    /**
     * Releases resources held by this instance.
     */
    void dispose();

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    boolean isDisposed();
}
