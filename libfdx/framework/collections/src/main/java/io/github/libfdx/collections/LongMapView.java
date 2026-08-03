package io.github.libfdx.collections;

/**
 * Provides read-only access to a live map with primitive long keys.
 *
 * @param <V> the value type
 * @author xpenatan
 */
public interface LongMapView<V> {
    V get(long key);

    V get(long key, V defaultValue);

    boolean containsKey(long key);

    boolean containsValue(V value);

    boolean containsValue(V value, boolean identity);

    long findKey(V value, long defaultKey);

    long findKey(V value, boolean identity, long defaultKey);

    int size();

    boolean isEmpty();

    boolean notEmpty();

    /**
     * Returns the entries. The iterator may reuse one mutable entry object.
     *
     * @return the entries
     */
    ObjectIterable<? extends LongMapEntry<V>> entries();

    LongIterable keys();

    ObjectIterable<V> values();
}
