package io.github.libfdx.collections;

/**
 * Supplies a reusable iterator over primitive {@code int} values.
 *
 * <p>This standalone libFDX contract returns primitive values without boxing.
 * {@link #iterator()} resets and returns the same collection-owned iterator.
 * Nested or concurrent iteration over the same iterable is unsupported.</p>
 *
 * @author xpenatan
 */
public interface IntIterable {
    /**
     * Resets and returns the reusable primitive iterator.
     *
     * @return the reusable iterator
     */
    IntIterator iterator();
}
