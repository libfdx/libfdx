package io.github.libfdx.collections;

/**
 * Provides read-only access to a live object map.
 *
 * <p>The view does not copy its source. Changes made through the owning map
 * are visible through the view, while the view itself exposes no mutation
 * operations. Stored {@code null} values are distinguished from missing keys
 * through {@link #containsKey(Object)}.</p>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author xpenatan
 */
public interface ObjectMapView<K, V> {
    /**
     * Returns a value.
     *
     * @param key the key
     * @return the value, or null
     */
    V get(K key);

    /**
     * Returns a value or a default value when the key is absent.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the stored value or default value
     */
    V get(K key, V defaultValue);

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    boolean containsKey(K key);

    /**
     * Returns whether a value exists using equals comparison.
     *
     * @param value the value
     * @return true if present
     */
    boolean containsValue(V value);

    /**
     * Returns whether a value exists.
     *
     * @param value the value
     * @param identity whether to compare values by identity
     * @return true if present
     */
    boolean containsValue(V value, boolean identity);

    /**
     * Returns the first key for a value using equals comparison.
     *
     * @param value the value
     * @return the key, or null
     */
    K findKey(V value);

    /**
     * Returns the first key for a value.
     *
     * @param value the value
     * @param identity whether to compare values by identity
     * @return the key, or null
     */
    K findKey(V value, boolean identity);

    /**
     * Returns the number of entries.
     *
     * @return the number of entries
     */
    int size();

    /**
     * Returns whether this view is empty.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Returns whether this view has at least one entry.
     *
     * @return true if not empty
     */
    boolean notEmpty();

    /**
     * Returns an iterable view over entries.
     *
     * @return the entries
     */
    Iterable<? extends ObjectMapEntry<K, V>> entries();

    /**
     * Returns an iterable view over keys.
     *
     * @return the keys
     */
    Iterable<K> keys();

    /**
     * Returns an iterable view over values.
     *
     * @return the values
     */
    Iterable<V> values();
}
