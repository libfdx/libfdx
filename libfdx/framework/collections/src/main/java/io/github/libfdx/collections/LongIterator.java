package io.github.libfdx.collections;

/**
 * Iterates primitive {@code long} values without boxing.
 *
 * @author xpenatan
 */
public interface LongIterator {
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
    long nextLong();

    /**
     * Restarts iteration from the beginning.
     *
     * @return this iterator
     */
    LongIterator reset();
}
