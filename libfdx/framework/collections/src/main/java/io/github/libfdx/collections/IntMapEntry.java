package io.github.libfdx.collections;

/**
 * Provides read-only access to one primitive-int map entry.
 *
 * <p>Entry iterators may reuse their entry instance. Copy the primitive key
 * and value before advancing when they must be retained.</p>
 *
 * @param <V> the value type
 * @author xpenatan
 */
public interface IntMapEntry<V> {
    /** @return the primitive key */
    int key();

    /** @return the mapped value */
    V value();
}
