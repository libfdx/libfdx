package io.github.libfdx.collections;

/**
 * Iterates primitive {@code float} values without boxing.
 *
 * @author xpenatan
 */
public interface FloatIterator {
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
    float nextFloat();

    /**
     * Restarts iteration from the beginning.
     *
     * @return this iterator
     */
    FloatIterator reset();
}
