package io.github.libfdx.collections;

/**
 * Supplies a reusable iterator over object values.
 *
 * <p>{@link #iterator()} resets and returns the same collection-owned iterator
 * on every call. Requesting it again invalidates an active traversal, so nested
 * or concurrent iteration over the same iterable is unsupported. This is a
 * standalone libFDX contract, so callers use the libFDX iterator explicitly.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public interface ObjectIterable<T> {
    /**
     * Resets and returns the reusable iterator.
     *
     * @return the reusable iterator
     */
    ObjectIterator<T> iterator();
}
