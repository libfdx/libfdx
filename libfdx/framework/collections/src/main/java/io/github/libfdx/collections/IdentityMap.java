package io.github.libfdx.collections;

/**
 * Maps object keys by reference identity using an open-addressed table.
 *
 * <p>Key lookup uses {@code ==} and {@link System#identityHashCode(Object)}.
 * This is useful for caches keyed by resource or graph-node instances whose
 * logical equality must not merge distinct objects.</p>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author xpenatan
 */
public final class IdentityMap<K, V> extends ObjectMap<K, V> {
    /**
     * Creates an identity map.
     */
    public IdentityMap() {
        super();
    }

    /**
     * Creates an identity map.
     *
     * @param capacity the expected capacity
     */
    public IdentityMap(int capacity) {
        super(capacity);
    }

    /**
     * Creates an identity map containing a copy of the supplied entries.
     *
     * @param values the entries
     */
    public IdentityMap(ObjectMapView<? extends K, ? extends V> values) {
        super(values != null ? values.size() : 0);
        if (values != null) {
            putAll(values);
        }
    }

    /**
     * Creates an identity map.
     *
     * @param capacity the expected capacity
     * @param loadFactor the load factor
     */
    public IdentityMap(int capacity, float loadFactor) {
        super(capacity, loadFactor);
    }

    @Override
    protected int keyHash(K key) {
        return System.identityHashCode(key);
    }

    @Override
    protected boolean keysEqual(K key, Object storedKey) {
        return key == storedKey;
    }
}
