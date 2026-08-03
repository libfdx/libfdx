package io.github.libfdx.collections;

/**
 * Supplies a reusable iterator over primitive {@code boolean} values.
 *
 * <p>{@link #iterator()} resets and returns the same collection-owned iterator.
 * Nested or concurrent iteration over the same iterable is unsupported.</p>
 *
 * @author xpenatan
 */
public interface BooleanIterable {
    /**
     * Resets and returns the reusable primitive iterator.
     *
     * @return the reusable iterator
     */
    BooleanIterator iterator();
}
