package io.github.libfdx.collections;

/**
 * Provides read-only access to one object-map entry.
 *
 * <p>Entry iterators reuse their entry instance. A caller that needs to retain
 * an entry must copy its key and value.</p>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author xpenatan
 */
public interface ObjectMapEntry<K, V> {
    /**
     * Returns the key.
     *
     * @return the key
     */
    K key();

    /**
     * Returns the value.
     *
     * @return the value
     */
    V value();
}
