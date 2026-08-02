package io.github.libfdx.collections;

/**
 * Iterates object values using reusable collection-owned state.
 *
 * <p>Calling {@link #reset()} restarts traversal without allocating another
 * iterator. Implementations are not thread-safe.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public interface ObjectIterator<T> {
    /**
     * Returns whether another value is available.
     *
     * @return true when another value is available
     */
    boolean hasNext();

    /**
     * Returns the next value.
     *
     * @return the next value
     */
    T next();

    /**
     * Restarts iteration from the beginning.
     *
     * @return this iterator
     */
    ObjectIterator<T> reset();
}
