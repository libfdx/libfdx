package io.github.libfdx.collections;

/**
 * Provides read-only access to a live map with primitive int keys.
 *
 * @param <V> the value type
 * @author xpenatan
 */
public interface IntMapView<V> {
    V get(int key);

    V get(int key, V defaultValue);

    boolean containsKey(int key);

    boolean containsValue(V value);

    boolean containsValue(V value, boolean identity);

    int findKey(V value, int defaultKey);

    int findKey(V value, boolean identity, int defaultKey);

    int size();

    boolean isEmpty();

    boolean notEmpty();

    /**
     * Returns the entries. The iterator may reuse one mutable entry object.
     *
     * @return the entries
     */
    ObjectIterable<? extends IntMapEntry<V>> entries();

    IntIterable keys();

    ObjectIterable<V> values();
}
