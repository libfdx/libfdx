package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Maps object keys to object values with a compact open-addressed table.
 *
 * <p><b>Algorithm:</b> A power-of-two hash table uses linear probing and
 * tombstones. Keys use {@link KeyComparison#EQUALITY} by default or
 * {@link KeyComparison#IDENTITY} when requested.</p>
 *
 * <p><b>Ordering:</b> Unordered. Iteration follows occupied table slots; it
 * does not preserve insertion order and may change after mutation or
 * resizing.</p>
 *
 * <p><b>Performance:</b> Put is expected amortized {@code O(1)}, and get,
 * key lookup, and removal are expected {@code O(1)} with a well-distributed
 * hash. Collision-heavy worst cases are {@code O(n)}. Full iteration scans
 * the backing table and is {@code O(table capacity)}.</p>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author xpenatan
 */
public final class ObjectMap<K, V> implements ObjectMapView<K, V> {
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private Object[] keys;
    private Object[] values;
    private byte[] states;
    private int size;
    private int occupied;
    private int threshold;
    private final float loadFactor;
    private final KeyComparison keyComparison;
    private Entries<K, V> entries;
    private Keys<K, V> keysView;
    private Values<K, V> valuesView;
    private ObjectMapView<K, V> view;

    /**
     * Creates a map.
     */
    public ObjectMap() {
        this(32, DEFAULT_LOAD_FACTOR, KeyComparison.EQUALITY);
    }

    /**
     * Creates a map using the requested key comparison.
     *
     * @param keyComparison the key comparison
     */
    public ObjectMap(KeyComparison keyComparison) {
        this(32, DEFAULT_LOAD_FACTOR, keyComparison);
    }

    /**
     * Creates a map.
     *
     * @param capacity the expected capacity
     */
    public ObjectMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR, KeyComparison.EQUALITY);
    }

    /**
     * Creates a map using the requested key comparison.
     *
     * @param capacity the expected capacity
     * @param keyComparison the key comparison
     */
    public ObjectMap(int capacity, KeyComparison keyComparison) {
        this(capacity, DEFAULT_LOAD_FACTOR, keyComparison);
    }

    /**
     * Creates a map containing a copy of the supplied entries.
     *
     * @param values the entries
     */
    public ObjectMap(ObjectMapView<? extends K, ? extends V> values) {
        this(values, KeyComparison.EQUALITY);
    }

    /**
     * Creates a map containing a copy of the supplied entries using the
     * requested key comparison.
     *
     * @param values the entries
     * @param keyComparison the key comparison
     */
    public ObjectMap(ObjectMapView<? extends K, ? extends V> values, KeyComparison keyComparison) {
        this(values != null ? values.size() : 0, DEFAULT_LOAD_FACTOR, keyComparison);
        if (values != null) {
            putAll(values);
        }
    }

    /**
     * Creates a map.
     *
     * @param capacity the expected capacity
     * @param loadFactor the load factor
     */
    public ObjectMap(int capacity, float loadFactor) {
        this(capacity, loadFactor, KeyComparison.EQUALITY);
    }

    /**
     * Creates a map using the requested key comparison.
     *
     * @param capacity the expected capacity
     * @param loadFactor the load factor
     * @param keyComparison the key comparison
     */
    public ObjectMap(int capacity, float loadFactor, KeyComparison keyComparison) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        CollectionHash.checkLoadFactor(loadFactor);
        if (keyComparison == null) {
            throw new IllegalArgumentException("keyComparison must not be null");
        }
        this.loadFactor = loadFactor;
        this.keyComparison = keyComparison;
        allocate(CollectionHash.tableSize(capacity, loadFactor));
    }

    /**
     * Adds or replaces a value.
     *
     * @param key the key
     * @param value the value
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        requireKey(key);
        if (occupied == keys.length) {
            resize(keys.length);
        }
        int index = locate(key);
        if (index >= 0) {
            Object old = values[index];
            values[index] = value;
            return (V)old;
        }
        if (size + 1 > threshold) {
            resize(keys.length << 1);
            index = locate(key);
        } else if (occupied + 1 > threshold) {
            resize(keys.length);
            index = locate(key);
        }
        index = -index - 1;
        if (states[index] == CollectionHash.EMPTY) {
            occupied++;
        }
        states[index] = CollectionHash.USED;
        keys[index] = key;
        values[index] = value;
        size++;
        return null;
    }

    /**
     * Adds or replaces every entry from a read-only map view.
     *
     * @param entries the entries
     * @return this map
     */
    public ObjectMap<K, V> putAll(ObjectMapView<? extends K, ? extends V> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        ensureCapacity(entries.size());
        ObjectIterator<? extends ObjectMapEntry<? extends K, ? extends V>> iterator =
                entries.entries().iterator();
        while (iterator.hasNext()) {
            ObjectMapEntry<? extends K, ? extends V> entry = iterator.next();
            put(entry.key(), entry.value());
        }
        return this;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @return the value, or null
     */
    @SuppressWarnings("unchecked")
    public V get(K key) {
        requireKey(key);
        int index = locate(key);
        return index >= 0 ? (V)values[index] : null;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the value or default
     */
    public V get(K key, V defaultValue) {
        requireKey(key);
        int index = locate(key);
        return index >= 0 ? valueAt(values, index) : defaultValue;
    }

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean containsKey(K key) {
        requireKey(key);
        return locate(key) >= 0;
    }

    /**
     * Returns whether a value exists using equals comparison.
     *
     * @param value the value
     * @return true if present
     */
    public boolean containsValue(V value) {
        return containsValue(value, false);
    }

    /**
     * Returns whether a value exists.
     *
     * @param value the value
     * @param identity whether to compare values by identity
     * @return true if present
     */
    public boolean containsValue(V value, boolean identity) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == CollectionHash.USED && valueMatches(values[i], value, identity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first key for a value using equals comparison.
     *
     * @param value the value
     * @return the key, or null
     */
    public K findKey(V value) {
        return findKey(value, false);
    }

    /**
     * Returns the first key for a value.
     *
     * @param value the value
     * @param identity whether to compare values by identity
     * @return the key, or null
     */
    public K findKey(V value, boolean identity) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == CollectionHash.USED && valueMatches(values[i], value, identity)) {
                return keyAt(keys, i);
            }
        }
        return null;
    }

    /**
     * Removes a key.
     *
     * @param key the key
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        requireKey(key);
        int index = locate(key);
        if (index < 0) {
            return null;
        }
        Object old = values[index];
        states[index] = CollectionHash.REMOVED;
        keys[index] = null;
        values[index] = null;
        size--;
        return (V)old;
    }

    /**
     * Removes all entries.
     */
    public void clear() {
        Arrays.fill(states, CollectionHash.EMPTY);
        Arrays.fill(keys, null);
        Arrays.fill(values, null);
        size = 0;
        occupied = 0;
    }

    /**
     * Ensures additional entry capacity.
     *
     * @param additionalCapacity the additional entries to reserve
     * @return this map
     */
    public ObjectMap<K, V> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > threshold) {
            resize(CollectionHash.tableSize(required, loadFactor));
        }
        else if (occupied + additionalCapacity > threshold) {
            resize(keys.length);
        }
        return this;
    }

    /**
     * Shrinks and compacts storage for the current entries.
     *
     * @return this map
     */
    public ObjectMap<K, V> shrink() {
        int tableSize = CollectionHash.tableSize(size, loadFactor);
        if (tableSize < keys.length || occupied != size) {
            resize(tableSize);
        }
        return this;
    }

    /**
     * Returns the number of entries.
     *
     * @return the number of entries
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this map is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this map has at least one entry.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return size > 0;
    }

    /**
     * Returns the current table capacity.
     *
     * @return the current table capacity
     */
    public int capacity() {
        return keys.length;
    }

    /**
     * Returns how keys are compared and hashed.
     *
     * @return the key comparison
     */
    public KeyComparison keyComparison() {
        return keyComparison;
    }

    /**
     * Returns an iterable view over entries.
     *
     * <p>The iterator reuses one mutable entry object. Copy its key and value
     * before advancing when they must be retained.</p>
     *
     * @return the entries
     */
    public ObjectIterable<Entry<K, V>> entries() {
        if (entries == null) {
            entries = new Entries<K, V>(this);
        }
        return entries;
    }

    /**
     * Returns an iterable view over keys.
     *
     * @return the keys
     */
    public ObjectIterable<K> keys() {
        if (keysView == null) {
            keysView = new Keys<K, V>(this);
        }
        return keysView;
    }

    /**
     * Returns an iterable view over values.
     *
     * @return the values
     */
    public ObjectIterable<V> values() {
        if (valuesView == null) {
            valuesView = new Values<K, V>(this);
        }
        return valuesView;
    }

    /**
     * Returns a cached read-only live view of this map.
     *
     * @return the read-only view
     */
    public ObjectMapView<K, V> view() {
        if (view == null) {
            view = new ReadOnlyObjectMapView<K, V>(this);
        }
        return view;
    }

    private int locate(K key) {
        int mask = keys.length - 1;
        boolean identity = keyComparison == KeyComparison.IDENTITY;
        int hash = identity ? System.identityHashCode(key) : key.hashCode();
        int index = CollectionHash.mix(hash) & mask;
        int firstRemoved = -1;
        for (int probes = 0; probes < keys.length; probes++) {
            byte state = states[index];
            if (state == CollectionHash.EMPTY) {
                return -(firstRemoved >= 0 ? firstRemoved : index) - 1;
            }
            if (state == CollectionHash.USED
                    && (identity ? key == keys[index] : key.equals(keys[index]))) {
                return index;
            }
            if (state == CollectionHash.REMOVED && firstRemoved < 0) {
                firstRemoved = index;
            }
            index = (index + 1) & mask;
        }
        return -(firstRemoved >= 0 ? firstRemoved : 0) - 1;
    }

    private void allocate(int tableSize) {
        keys = new Object[tableSize];
        values = new Object[tableSize];
        states = new byte[tableSize];
        threshold = CollectionHash.threshold(tableSize, loadFactor);
        occupied = 0;
    }

    private void resize(int tableSize) {
        Object[] oldKeys = keys;
        Object[] oldValues = values;
        byte[] oldStates = states;
        allocate(tableSize);
        int oldSize = size;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStates[i] == CollectionHash.USED) {
                put(keyAt(oldKeys, i), valueAt(oldValues, i));
            }
        }
        if (size != oldSize) {
            throw new IllegalStateException("rehash lost entries");
        }
    }

    private void requireKey(K key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
    }

    @SuppressWarnings("unchecked")
    private K keyAt(Object[] keys, int index) {
        return (K)keys[index];
    }

    @SuppressWarnings("unchecked")
    private V valueAt(Object[] values, int index) {
        return (V)values[index];
    }

    private boolean valueMatches(Object candidate, V value, boolean identity) {
        if (identity) {
            return candidate == value;
        }
        return value == null ? candidate == null : value.equals(candidate);
    }

    /**
     * Represents a map entry.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @author xpenatan
     */
    public static final class Entry<K, V> implements ObjectMapEntry<K, V> {
        private K key;
        private V value;

        private Entry() {
        }

        /**
         * Returns the key.
         *
         * @return the key
         */
        public K key() {
            return key;
        }

        /**
         * Returns the value.
         *
         * @return the value
         */
        public V value() {
            return value;
        }
    }

    private static final class Entries<K, V> implements ObjectIterable<Entry<K, V>> {
        private final ObjectMap<K, V> map;
        private EntryIterator<K, V> iterator;

        Entries(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<K, V>> iterator() {
            if (iterator == null) {
                iterator = new EntryIterator<K, V>(map);
            }
            return iterator.reset();
        }
    }

    private static final class EntryIterator<K, V> implements ObjectIterator<Entry<K, V>> {
        private final ObjectMap<K, V> map;
        private final Entry<K, V> entry = new Entry<K, V>();
        private int nextIndex;
        private int returned;

        EntryIterator(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<K, V>> reset() {
            entry.key = null;
            entry.value = null;
            nextIndex = 0;
            returned = 0;
            findNext();
            return this;
        }

        @Override
        public boolean hasNext() {
            boolean available = returned < map.size;
            if (!available) {
                entry.key = null;
                entry.value = null;
            }
            return available;
        }

        @Override
        public Entry<K, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            entry.key = map.keyAt(map.keys, index);
            entry.value = map.valueAt(map.values, index);
            returned++;
            nextIndex++;
            findNext();
            return entry;
        }

        private void findNext() {
            while (nextIndex < map.states.length && map.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }

    private static final class Keys<K, V> implements ObjectIterable<K> {
        private final ObjectMap<K, V> map;
        private KeyIterator<K, V> iterator;

        Keys(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<K> iterator() {
            if (iterator == null) {
                iterator = new KeyIterator<K, V>(map);
            }
            return iterator.reset();
        }
    }

    private static final class KeyIterator<K, V> implements ObjectIterator<K> {
        private final ObjectMap<K, V> map;
        private int nextIndex;
        private int returned;

        KeyIterator(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<K> reset() {
            nextIndex = 0;
            returned = 0;
            findNext();
            return this;
        }

        @Override
        public boolean hasNext() {
            return returned < map.size;
        }

        @Override
        public K next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            returned++;
            nextIndex++;
            findNext();
            return map.keyAt(map.keys, index);
        }

        private void findNext() {
            while (nextIndex < map.states.length && map.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }

    private static final class Values<K, V> implements ObjectIterable<V> {
        private final ObjectMap<K, V> map;
        private ValueIterator<K, V> iterator;

        Values(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<V> iterator() {
            if (iterator == null) {
                iterator = new ValueIterator<K, V>(map);
            }
            return iterator.reset();
        }
    }

    private static final class ValueIterator<K, V> implements ObjectIterator<V> {
        private final ObjectMap<K, V> map;
        private int nextIndex;
        private int returned;

        ValueIterator(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<V> reset() {
            nextIndex = 0;
            returned = 0;
            findNext();
            return this;
        }

        @Override
        public boolean hasNext() {
            return returned < map.size;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            returned++;
            nextIndex++;
            findNext();
            return map.valueAt(map.values, index);
        }

        private void findNext() {
            while (nextIndex < map.states.length && map.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }

    private static final class ReadOnlyObjectMapView<K, V> implements ObjectMapView<K, V> {
        private final ObjectMap<K, V> map;

        ReadOnlyObjectMapView(ObjectMap<K, V> map) {
            this.map = map;
        }

        @Override
        public V get(K key) {
            return map.get(key);
        }

        @Override
        public V get(K key, V defaultValue) {
            return map.get(key, defaultValue);
        }

        @Override
        public boolean containsKey(K key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(V value) {
            return map.containsValue(value);
        }

        @Override
        public boolean containsValue(V value, boolean identity) {
            return map.containsValue(value, identity);
        }

        @Override
        public K findKey(V value) {
            return map.findKey(value);
        }

        @Override
        public K findKey(V value, boolean identity) {
            return map.findKey(value, identity);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public boolean notEmpty() {
            return map.notEmpty();
        }

        @Override
        public ObjectIterable<? extends ObjectMapEntry<K, V>> entries() {
            return map.entries();
        }

        @Override
        public ObjectIterable<K> keys() {
            return map.keys();
        }

        @Override
        public ObjectIterable<V> values() {
            return map.values();
        }
    }
}
