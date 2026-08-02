package io.github.libfdx.collections;

/**
 * Iterates primitive {@code int} values without boxing.
 *
 * @author xpenatan
 */
public interface IntIterator {
    /**
     * Returns whether another value is available.
     *
     * @return true when another value is available
     */
    boolean hasNext();

    /**
     * Returns the next primitive value.
     *
     * @return the next value
     */
    int nextInt();

    /**
     * Restarts iteration from the beginning.
     *
     * @return this iterator
     */
    IntIterator reset();
}
