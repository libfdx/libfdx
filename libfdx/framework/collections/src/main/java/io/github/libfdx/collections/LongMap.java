package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Maps primitive long keys to object values without boxing keys.
 *
 * <p><b>Algorithm:</b> A power-of-two open-addressed hash table uses linear
 * probing and tombstones.</p>
 *
 * <p><b>Ordering:</b> Unordered. Iteration follows occupied table slots; it
 * does not preserve insertion order and may change after mutation or
 * resizing.</p>
 *
 * <p><b>Performance:</b> Put is expected amortized {@code O(1)}, and get,
 * key lookup, and removal are expected {@code O(1)} with well-distributed
 * keys. Collision-heavy worst cases are {@code O(n)}. Full iteration scans
 * the backing table and is {@code O(table capacity)}.</p>
 *
 * @param <V> the value type
 * @author xpenatan
 */
public final class LongMap<V> implements LongMapView<V> {
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private long[] keys;
    private Object[] values;
    private byte[] states;
    private int size;
    private int occupied;
    private int threshold;
    private final float loadFactor;
    private Entries<V> entries;
    private Keys keysView;
    private Values<V> valuesView;
    private LongMapView<V> view;

    /**
     * Creates a map.
     */
    public LongMap() {
        this(32, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a map.
     *
     * @param capacity the expected capacity
     */
    public LongMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a map containing a copy of the supplied entries.
     *
     * @param values the entries
     */
    public LongMap(LongMapView<? extends V> values) {
        this(values != null ? values.size() : 0, DEFAULT_LOAD_FACTOR);
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
    public LongMap(int capacity, float loadFactor) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        CollectionHash.checkLoadFactor(loadFactor);
        this.loadFactor = loadFactor;
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
    public V put(long key, V value) {
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
    public LongMap<V> putAll(LongMapView<? extends V> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        ensureCapacity(entries.size());
        ObjectIterator<? extends Entry<? extends V>> iterator = entries.entries().iterator();
        while (iterator.hasNext()) {
            Entry<? extends V> entry = iterator.next();
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
    public V get(long key) {
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
    public V get(long key, V defaultValue) {
        int index = locate(key);
        return index >= 0 ? valueAt(values, index) : defaultValue;
    }

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean containsKey(long key) {
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
     * @param defaultKey the key returned when no value matches
     * @return the key or default key
     */
    public long findKey(V value, long defaultKey) {
        return findKey(value, false, defaultKey);
    }

    /**
     * Returns the first key for a value.
     *
     * @param value the value
     * @param identity whether to compare values by identity
     * @param defaultKey the key returned when no value matches
     * @return the key or default key
     */
    public long findKey(V value, boolean identity, long defaultKey) {
        for (int i = 0; i < states.length; i++) {
            if (states[i] == CollectionHash.USED && valueMatches(values[i], value, identity)) {
                return keys[i];
            }
        }
        return defaultKey;
    }

    /**
     * Removes a key.
     *
     * @param key the key
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int index = locate(key);
        if (index < 0) {
            return null;
        }
        Object old = values[index];
        states[index] = CollectionHash.REMOVED;
        values[index] = null;
        size--;
        return (V)old;
    }

    /**
     * Removes all entries.
     */
    public void clear() {
        Arrays.fill(states, CollectionHash.EMPTY);
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
    public LongMap<V> ensureCapacity(int additionalCapacity) {
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
    public LongMap<V> shrink() {
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
     * Returns an iterable view over entries.
     *
     * <p>The iterator reuses one mutable entry object. Copy its key and value
     * before advancing when they must be retained.</p>
     *
     * @return the entries
     */
    public ObjectIterable<Entry<V>> entries() {
        if (entries == null) {
            entries = new Entries<V>(this);
        }
        return entries;
    }

    /**
     * Returns an iterable view over primitive keys.
     *
     * @return the keys
     */
    public Keys keys() {
        if (keysView == null) {
            keysView = new Keys(this);
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
            valuesView = new Values<V>(this);
        }
        return valuesView;
    }

    /**
     * Returns a cached read-only live view of this map.
     *
     * @return the read-only view
     */
    public LongMapView<V> view() {
        if (view == null) {
            view = new ReadOnlyLongMapView<V>(this);
        }
        return view;
    }

    private int locate(long key) {
        int mask = keys.length - 1;
        int index = CollectionHash.mix(key) & mask;
        int firstRemoved = -1;
        for (int probes = 0; probes < keys.length; probes++) {
            byte state = states[index];
            if (state == CollectionHash.EMPTY) {
                return -(firstRemoved >= 0 ? firstRemoved : index) - 1;
            }
            if (state == CollectionHash.USED && keys[index] == key) {
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
        keys = new long[tableSize];
        values = new Object[tableSize];
        states = new byte[tableSize];
        threshold = CollectionHash.threshold(tableSize, loadFactor);
        occupied = 0;
    }

    private void resize(int tableSize) {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        byte[] oldStates = states;
        allocate(tableSize);
        int oldSize = size;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStates[i] == CollectionHash.USED) {
                put(oldKeys[i], valueAt(oldValues, i));
            }
        }
        if (size != oldSize) {
            throw new IllegalStateException("rehash lost entries");
        }
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
     * Represents a long-key map entry.
     *
     * @param <V> the value type
     * @author xpenatan
     */
    public static final class Entry<V> {
        private long key;
        private V value;

        private Entry() {
        }

        /**
         * Returns the key.
         *
         * @return the key
         */
        public long key() {
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

    private static final class Entries<V> implements ObjectIterable<Entry<V>> {
        private final LongMap<V> map;
        private EntryIterator<V> iterator;

        Entries(LongMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<V>> iterator() {
            if (iterator == null) {
                iterator = new EntryIterator<V>(map);
            }
            return iterator.reset();
        }
    }

    private static final class EntryIterator<V> implements ObjectIterator<Entry<V>> {
        private final LongMap<V> map;
        private final Entry<V> entry = new Entry<V>();
        private int nextIndex;
        private int returned;

        EntryIterator(LongMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<V>> reset() {
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
                entry.value = null;
            }
            return available;
        }

        @Override
        public Entry<V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            entry.key = map.keys[index];
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

    /**
     * Traversal view over long keys.
     *
     * @author xpenatan
     */
    public static final class Keys implements LongIterable {
        private final LongMap<?> map;
        private KeyIterator iterator;

        private Keys(LongMap<?> map) {
            this.map = map;
        }

        @Override
        public KeyIterator iterator() {
            if (iterator == null) {
                iterator = new KeyIterator(map);
            }
            iterator.reset();
            return iterator;
        }
    }

    /**
     * Iterator over long keys.
     *
     * @author xpenatan
     */
    public static final class KeyIterator implements LongIterator {
        private final LongMap<?> map;
        private int nextIndex;
        private int returned;

        private KeyIterator(LongMap<?> map) {
            this.map = map;
        }

        @Override
        public LongIterator reset() {
            nextIndex = 0;
            returned = 0;
            findNext();
            return this;
        }

        @Override
        public boolean hasNext() {
            return returned < map.size;
        }

        /**
         * Returns the next primitive key.
         *
         * @return the next key
         */
        @Override
        public long nextLong() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            returned++;
            nextIndex++;
            findNext();
            return map.keys[index];
        }

        private void findNext() {
            while (nextIndex < map.states.length && map.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }

    private static final class Values<V> implements ObjectIterable<V> {
        private final LongMap<V> map;
        private ValueIterator<V> iterator;

        Values(LongMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<V> iterator() {
            if (iterator == null) {
                iterator = new ValueIterator<V>(map);
            }
            return iterator.reset();
        }
    }

    private static final class ValueIterator<V> implements ObjectIterator<V> {
        private final LongMap<V> map;
        private int nextIndex;
        private int returned;

        ValueIterator(LongMap<V> map) {
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

    private static final class ReadOnlyLongMapView<V> implements LongMapView<V> {
        private final LongMap<V> map;

        ReadOnlyLongMapView(LongMap<V> map) {
            this.map = map;
        }

        @Override
        public V get(long key) {
            return map.get(key);
        }

        @Override
        public V get(long key, V defaultValue) {
            return map.get(key, defaultValue);
        }

        @Override
        public boolean containsKey(long key) {
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
        public long findKey(V value, long defaultKey) {
            return map.findKey(value, defaultKey);
        }

        @Override
        public long findKey(V value, boolean identity, long defaultKey) {
            return map.findKey(value, identity, defaultKey);
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
        public ObjectIterable<Entry<V>> entries() {
            return map.entries();
        }

        @Override
        public LongIterable keys() {
            return map.keys();
        }

        @Override
        public ObjectIterable<V> values() {
            return map.values();
        }
    }
}
