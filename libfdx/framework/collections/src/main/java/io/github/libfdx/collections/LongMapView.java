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

    Iterable<LongMap.Entry<V>> entries();

    LongMap.Keys keys();

    Iterable<V> values();
}
