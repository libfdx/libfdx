package io.github.libfdx.collections;

/**
 * Iterates primitive {@code boolean} values without boxing.
 *
 * @author xpenatan
 */
public interface BooleanIterator {
    /** @return true when another value is available */
    boolean hasNext();

    /** @return the next primitive value */
    boolean nextBoolean();

    /** @return this iterator, restarted from the beginning */
    BooleanIterator reset();
}
