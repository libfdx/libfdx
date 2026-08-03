package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Maps compact non-negative int keys to values with direct sparse lookup,
 * packed dense storage, and stable ordered traversal, without entry nodes.
 *
 * <p><b>Algorithm:</b> A sparse int array maps each key to a one-based index in
 * packed key and value arrays. Removal swaps the last dense entry into the
 * removed slot and repairs its sparse index. Separate primitive previous-key
 * and next-key arrays maintain the logical sequence. No entry object is
 * created, pooled, or discarded by put or remove.</p>
 *
 * <p><b>Ordering:</b> {@link #entries()}, {@link #keys()}, and
 * {@link #values()} follow insertion order, as modified by
 * {@link #moveToFirst(int)} and {@link #moveToLast(int)}. Dense access through
 * {@link #keyAt(int)}, {@link #valueAt(int)}, {@link #denseKeys()}, or
 * {@link #denseValues()} is unordered and may change after removal.</p>
 *
 * <p><b>Performance:</b> With sufficient reserved capacity, put, get,
 * contains-key, key removal, dense indexed access, first/last-key access, and
 * order moves are worst-case {@code O(1)}. A put that grows sparse-key or dense
 * entry storage is {@code O(new capacity)}; geometric growth makes gradual
 * expansion amortized {@code O(1)}. Value lookup and either complete traversal
 * are {@code O(n)}. Iteration over ordered views visits exactly {@code n}
 * entries, while dense iteration is contiguous.</p>
 *
 * <p><b>Key range:</b> Storage for sparse indices and order links is
 * proportional to the largest addressable key. This class is intended for
 * compact IDs such as entity or component IDs; {@link IntMap} is more
 * appropriate for negative or widely separated keys.</p>
 *
 * <p><b>Allocation:</b> Mutations reuse primitive and object arrays until they
 * grow. Iterable views and their iterators are cached after first use. Entry
 * iteration reuses one mutable {@link Entry}; copy its key and value before
 * advancing when they must be retained. Nested or concurrent iteration over
 * the same view is unsupported.</p>
 *
 * @param <V> the value type
 * @author xpenatan
 */
public final class OrderedIntSparseMap<V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final int NO_KEY = -1;

    private int[] sparseIndices;
    private int[] denseKeys;
    private Object[] denseValues;
    private int[] previousKeys;
    private int[] nextKeys;
    private int firstKey = NO_KEY;
    private int lastKey = NO_KEY;
    private int size;
    private Entries<V> entries;
    private Keys keysView;
    private Values<V> valuesView;
    private DenseKeys denseKeysView;
    private DenseValues<V> denseValuesView;

    /** Creates a sparse ordered map with default key and entry capacity. */
    public OrderedIntSparseMap() {
        this(DEFAULT_CAPACITY, DEFAULT_CAPACITY);
    }

    /**
     * Creates a map with the same initial key and entry capacity.
     *
     * @param capacity initial exclusive key limit and expected entry capacity
     */
    public OrderedIntSparseMap(int capacity) {
        this(capacity, capacity);
    }

    /**
     * Creates a map with independent initial key and entry capacities.
     *
     * @param keyCapacity initial exclusive key limit
     * @param entryCapacity expected entry capacity
     */
    public OrderedIntSparseMap(int keyCapacity, int entryCapacity) {
        if (keyCapacity < 0) {
            throw new IllegalArgumentException("keyCapacity must be >= 0");
        }
        if (entryCapacity < 0) {
            throw new IllegalArgumentException("entryCapacity must be >= 0");
        }
        sparseIndices = new int[keyCapacity];
        previousKeys = new int[keyCapacity];
        nextKeys = new int[keyCapacity];
        Arrays.fill(previousKeys, NO_KEY);
        Arrays.fill(nextKeys, NO_KEY);
        denseKeys = new int[entryCapacity];
        denseValues = new Object[entryCapacity];
    }

    /**
     * Adds or replaces a value. Replacement retains the logical order.
     *
     * @param key the non-negative key
     * @param value the value
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V put(int key, V value) {
        ensureKeyAddress(key);
        int encodedIndex = sparseIndices[key];
        if (encodedIndex != 0) {
            int denseIndex = encodedIndex - 1;
            V previous = (V)denseValues[denseIndex];
            denseValues[denseIndex] = value;
            return previous;
        }

        ensureDenseAddress(size + 1);
        int denseIndex = size;
        denseKeys[denseIndex] = key;
        denseValues[denseIndex] = value;
        sparseIndices[key] = denseIndex + 1;
        linkLast(key);
        size++;
        return null;
    }

    /**
     * Adds or replaces every entry from another sparse ordered map in its
     * current logical order.
     *
     * @param source the source map
     * @return this map
     */
    public OrderedIntSparseMap<V> putAll(OrderedIntSparseMap<? extends V> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        ensureCapacity(source.size);
        int key = source.firstKey;
        while (key != NO_KEY) {
            put(key, source.valueForKeyUnchecked(key));
            key = source.nextKeys[key];
        }
        return this;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @return the value, or null
     */
    public V get(int key) {
        int denseIndex = denseIndex(key);
        return denseIndex >= 0 ? valueAtUnchecked(denseIndex) : null;
    }

    /**
     * Returns a value or a default when the key is absent.
     *
     * @param key the key
     * @param defaultValue the value returned when absent
     * @return the stored value or default value
     */
    public V get(int key, V defaultValue) {
        int denseIndex = denseIndex(key);
        return denseIndex >= 0 ? valueAtUnchecked(denseIndex) : defaultValue;
    }

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean containsKey(int key) {
        return denseIndex(key) >= 0;
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
     * @param identity whether to compare by identity
     * @return true if present
     */
    public boolean containsValue(V value, boolean identity) {
        for (int i = 0; i < size; i++) {
            if (valuesEqual(value, denseValues[i], identity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first logically ordered key for a value using equals
     * comparison.
     *
     * @param value the value
     * @param defaultKey the key returned when no value matches
     * @return the key or default key
     */
    public int findKey(V value, int defaultKey) {
        return findKey(value, false, defaultKey);
    }

    /**
     * Returns the first logically ordered key for a value.
     *
     * @param value the value
     * @param identity whether to compare by identity
     * @param defaultKey the key returned when no value matches
     * @return the key or default key
     */
    public int findKey(V value, boolean identity, int defaultKey) {
        int key = firstKey;
        while (key != NO_KEY) {
            if (valuesEqual(value, valueForKeyUnchecked(key), identity)) {
                return key;
            }
            key = nextKeys[key];
        }
        return defaultKey;
    }

    /**
     * Removes a key using direct sparse lookup and dense tail swapping.
     *
     * @param key the key
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V remove(int key) {
        int denseIndex = denseIndex(key);
        if (denseIndex < 0) {
            return null;
        }

        V previous = (V)denseValues[denseIndex];
        unlink(key);
        sparseIndices[key] = 0;

        int lastDenseIndex = --size;
        if (denseIndex != lastDenseIndex) {
            int movedKey = denseKeys[lastDenseIndex];
            denseKeys[denseIndex] = movedKey;
            denseValues[denseIndex] = denseValues[lastDenseIndex];
            sparseIndices[movedKey] = denseIndex + 1;
        }
        denseKeys[lastDenseIndex] = 0;
        denseValues[lastDenseIndex] = null;
        return previous;
    }

    /**
     * Moves an existing key to the beginning of logical traversal. Dense
     * storage is unchanged.
     *
     * @param key the existing key
     * @return this map
     */
    public OrderedIntSparseMap<V> moveToFirst(int key) {
        requireKey(key);
        if (key == firstKey) {
            return this;
        }
        unlink(key);
        previousKeys[key] = NO_KEY;
        nextKeys[key] = firstKey;
        previousKeys[firstKey] = key;
        firstKey = key;
        return this;
    }

    /**
     * Moves an existing key to the end of logical traversal. Dense storage is
     * unchanged.
     *
     * @param key the existing key
     * @return this map
     */
    public OrderedIntSparseMap<V> moveToLast(int key) {
        requireKey(key);
        if (key == lastKey) {
            return this;
        }
        unlink(key);
        previousKeys[key] = lastKey;
        nextKeys[key] = NO_KEY;
        nextKeys[lastKey] = key;
        lastKey = key;
        return this;
    }

    /** Removes all entries while retaining allocated storage. */
    public void clear() {
        for (int i = 0; i < size; i++) {
            int key = denseKeys[i];
            sparseIndices[key] = 0;
            previousKeys[key] = NO_KEY;
            nextKeys[key] = NO_KEY;
            denseKeys[i] = 0;
            denseValues[i] = null;
        }
        size = 0;
        firstKey = NO_KEY;
        lastKey = NO_KEY;
    }

    /**
     * Ensures that additional entries can be inserted without growing dense
     * storage.
     *
     * @param additionalCapacity additional entry capacity
     * @return this map
     */
    public OrderedIntSparseMap<V> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        long required = (long)size + additionalCapacity;
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("required capacity is too large");
        }
        if (required > denseKeys.length) {
            resizeDense((int)required);
        }
        return this;
    }

    /**
     * Ensures every key below the exclusive capacity can be addressed without
     * growing sparse storage.
     *
     * @param keyCapacity exclusive key capacity
     * @return this map
     */
    public OrderedIntSparseMap<V> ensureKeyCapacity(int keyCapacity) {
        if (keyCapacity < 0) {
            throw new IllegalArgumentException("keyCapacity must be >= 0");
        }
        if (keyCapacity <= sparseIndices.length) {
            return this;
        }
        int oldCapacity = sparseIndices.length;
        sparseIndices = Arrays.copyOf(sparseIndices, keyCapacity);
        previousKeys = Arrays.copyOf(previousKeys, keyCapacity);
        nextKeys = Arrays.copyOf(nextKeys, keyCapacity);
        Arrays.fill(previousKeys, oldCapacity, keyCapacity, NO_KEY);
        Arrays.fill(nextKeys, oldCapacity, keyCapacity, NO_KEY);
        return this;
    }

    /**
     * Shrinks dense storage to the current size and sparse storage to one past
     * the largest active key.
     *
     * @return this map
     */
    public OrderedIntSparseMap<V> shrink() {
        if (denseKeys.length != size) {
            resizeDense(size);
        }
        int keyCapacity = 0;
        for (int i = 0; i < size; i++) {
            keyCapacity = Math.max(keyCapacity, denseKeys[i] + 1);
        }
        if (keyCapacity < sparseIndices.length) {
            sparseIndices = Arrays.copyOf(sparseIndices, keyCapacity);
            previousKeys = Arrays.copyOf(previousKeys, keyCapacity);
            nextKeys = Arrays.copyOf(nextKeys, keyCapacity);
        }
        return this;
    }

    /** @return the number of entries */
    public int size() {
        return size;
    }

    /** @return true when this map is empty */
    public boolean isEmpty() {
        return size == 0;
    }

    /** @return true when this map has at least one entry */
    public boolean notEmpty() {
        return size != 0;
    }

    /** @return the current exclusive sparse key capacity */
    public int keyCapacity() {
        return sparseIndices.length;
    }

    /** @return the current dense entry capacity */
    public int capacity() {
        return denseKeys.length;
    }

    /**
     * Returns the first key in logical order.
     *
     * @return the first key, or -1 when empty
     */
    public int firstKey() {
        return firstKey;
    }

    /**
     * Returns the last key in logical order.
     *
     * @return the last key, or -1 when empty
     */
    public int lastKey() {
        return lastKey;
    }

    /**
     * Returns the key at a dense unordered index.
     *
     * @param index the dense index
     * @return the key
     */
    public int keyAt(int index) {
        checkDenseIndex(index);
        return denseKeys[index];
    }

    /**
     * Returns the value at a dense unordered index.
     *
     * @param index the dense index
     * @return the value
     */
    public V valueAt(int index) {
        checkDenseIndex(index);
        return valueAtUnchecked(index);
    }

    /**
     * Returns a cached iterable over entries in logical order.
     *
     * <p>The iterator reuses one mutable entry object.</p>
     *
     * @return the ordered entries
     */
    public ObjectIterable<Entry<V>> entries() {
        if (entries == null) {
            entries = new Entries<V>(this);
        }
        return entries;
    }

    /**
     * Returns a cached primitive iterable over keys in logical order.
     *
     * @return the ordered keys
     */
    public IntIterable keys() {
        if (keysView == null) {
            keysView = new Keys(this, true);
        }
        return keysView;
    }

    /**
     * Returns a cached iterable over values in logical order.
     *
     * @return the ordered values
     */
    public ObjectIterable<V> values() {
        if (valuesView == null) {
            valuesView = new Values<V>(this);
        }
        return valuesView;
    }

    /**
     * Returns a cached primitive iterable over dense unordered keys.
     *
     * @return the dense keys
     */
    public IntIterable denseKeys() {
        if (denseKeysView == null) {
            denseKeysView = new DenseKeys(this);
        }
        return denseKeysView;
    }

    /**
     * Returns a cached read-only live view of dense unordered values.
     *
     * @return the dense values
     */
    public ArrayView<V> denseValues() {
        if (denseValuesView == null) {
            denseValuesView = new DenseValues<V>(this);
        }
        return denseValuesView;
    }

    private int denseIndex(int key) {
        if (key < 0 || key >= sparseIndices.length) {
            return -1;
        }
        return sparseIndices[key] - 1;
    }

    @SuppressWarnings("unchecked")
    private V valueAtUnchecked(int denseIndex) {
        return (V)denseValues[denseIndex];
    }

    private V valueForKeyUnchecked(int key) {
        return valueAtUnchecked(sparseIndices[key] - 1);
    }

    private void ensureKeyAddress(int key) {
        if (key < 0) {
            throw new IllegalArgumentException("key must be >= 0");
        }
        if (key < sparseIndices.length) {
            return;
        }
        if (key == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("key is too large");
        }
        int required = key + 1;
        int grown = grownCapacity(sparseIndices.length, required);
        ensureKeyCapacity(grown);
    }

    private void ensureDenseAddress(int required) {
        if (required > denseKeys.length) {
            resizeDense(grownCapacity(denseKeys.length, required));
        }
    }

    private static int grownCapacity(int current, int required) {
        long grown = Math.max(8L, (long)current + (current >> 1) + 1L);
        return (int)Math.min(Integer.MAX_VALUE, Math.max(grown, required));
    }

    private void resizeDense(int capacity) {
        denseKeys = Arrays.copyOf(denseKeys, capacity);
        denseValues = Arrays.copyOf(denseValues, capacity);
    }

    private void linkLast(int key) {
        previousKeys[key] = lastKey;
        nextKeys[key] = NO_KEY;
        if (lastKey != NO_KEY) {
            nextKeys[lastKey] = key;
        }
        else {
            firstKey = key;
        }
        lastKey = key;
    }

    private void unlink(int key) {
        int previous = previousKeys[key];
        int next = nextKeys[key];
        if (previous != NO_KEY) {
            nextKeys[previous] = next;
        }
        else {
            firstKey = next;
        }
        if (next != NO_KEY) {
            previousKeys[next] = previous;
        }
        else {
            lastKey = previous;
        }
        previousKeys[key] = NO_KEY;
        nextKeys[key] = NO_KEY;
    }

    private void requireKey(int key) {
        if (!containsKey(key)) {
            throw new IllegalArgumentException("key is not present: " + key);
        }
    }

    private void checkDenseIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private static boolean valuesEqual(Object value, Object other, boolean identity) {
        if (identity) {
            return value == other;
        }
        return value == null ? other == null : value.equals(other);
    }

    /**
     * Mutable entry reused by ordered entry iteration.
     *
     * @param <V> the value type
     */
    public static final class Entry<V> {
        private int key;
        private V value;

        private Entry() {
        }

        /** @return the primitive key */
        public int key() {
            return key;
        }

        /** @return the mapped value */
        public V value() {
            return value;
        }
    }

    private static final class Entries<V> implements ObjectIterable<Entry<V>> {
        private final OrderedIntSparseMap<V> map;
        private EntryIterator<V> iterator;

        Entries(OrderedIntSparseMap<V> map) {
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
        private final OrderedIntSparseMap<V> map;
        private final Entry<V> entry = new Entry<V>();
        private int nextKey;

        EntryIterator(OrderedIntSparseMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<V>> reset() {
            entry.value = null;
            nextKey = map.firstKey;
            return this;
        }

        @Override
        public boolean hasNext() {
            return nextKey != NO_KEY;
        }

        @Override
        public Entry<V> next() {
            if (nextKey == NO_KEY) {
                throw new NoSuchElementException();
            }
            int key = nextKey;
            nextKey = map.nextKeys[key];
            entry.key = key;
            entry.value = map.valueForKeyUnchecked(key);
            return entry;
        }
    }

    private static final class Keys implements IntIterable {
        private final OrderedIntSparseMap<?> map;
        private final boolean ordered;
        private KeyIterator iterator;

        Keys(OrderedIntSparseMap<?> map, boolean ordered) {
            this.map = map;
            this.ordered = ordered;
        }

        @Override
        public IntIterator iterator() {
            if (iterator == null) {
                iterator = new KeyIterator(map, ordered);
            }
            return iterator.reset();
        }
    }

    private static final class DenseKeys implements IntIterable {
        private final OrderedIntSparseMap<?> map;
        private KeyIterator iterator;

        DenseKeys(OrderedIntSparseMap<?> map) {
            this.map = map;
        }

        @Override
        public IntIterator iterator() {
            if (iterator == null) {
                iterator = new KeyIterator(map, false);
            }
            return iterator.reset();
        }
    }

    private static final class KeyIterator implements IntIterator {
        private final OrderedIntSparseMap<?> map;
        private final boolean ordered;
        private int nextKey;
        private int denseIndex;

        KeyIterator(OrderedIntSparseMap<?> map, boolean ordered) {
            this.map = map;
            this.ordered = ordered;
        }

        @Override
        public IntIterator reset() {
            nextKey = map.firstKey;
            denseIndex = 0;
            return this;
        }

        @Override
        public boolean hasNext() {
            return ordered ? nextKey != NO_KEY : denseIndex < map.size;
        }

        @Override
        public int nextInt() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (!ordered) {
                return map.denseKeys[denseIndex++];
            }
            int key = nextKey;
            nextKey = map.nextKeys[key];
            return key;
        }
    }

    private static final class Values<V> implements ObjectIterable<V> {
        private final OrderedIntSparseMap<V> map;
        private ValueIterator<V> iterator;

        Values(OrderedIntSparseMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<V> iterator() {
            if (iterator == null) {
                iterator = new ValueIterator<V>(map, true);
            }
            return iterator.reset();
        }
    }

    private static final class ValueIterator<V> implements ObjectIterator<V> {
        private final OrderedIntSparseMap<V> map;
        private final boolean ordered;
        private int nextKey;
        private int denseIndex;

        ValueIterator(OrderedIntSparseMap<V> map, boolean ordered) {
            this.map = map;
            this.ordered = ordered;
        }

        @Override
        public ObjectIterator<V> reset() {
            nextKey = map.firstKey;
            denseIndex = 0;
            return this;
        }

        @Override
        public boolean hasNext() {
            return ordered ? nextKey != NO_KEY : denseIndex < map.size;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (!ordered) {
                return map.valueAtUnchecked(denseIndex++);
            }
            int key = nextKey;
            nextKey = map.nextKeys[key];
            return map.valueForKeyUnchecked(key);
        }
    }

    private static final class DenseValues<V> implements ArrayView<V> {
        private final OrderedIntSparseMap<V> map;
        private ValueIterator<V> iterator;

        DenseValues(OrderedIntSparseMap<V> map) {
            this.map = map;
        }

        @Override
        public V get(int index) {
            return map.valueAt(index);
        }

        @Override
        public V first() {
            if (map.size == 0) {
                throw new NoSuchElementException("OrderedIntSparseMap is empty");
            }
            return map.valueAtUnchecked(0);
        }

        @Override
        public V peek() {
            if (map.size == 0) {
                throw new NoSuchElementException("OrderedIntSparseMap is empty");
            }
            return map.valueAtUnchecked(map.size - 1);
        }

        @Override
        public boolean contains(V value) {
            return indexOf(value, false) >= 0;
        }

        @Override
        public boolean contains(V value, boolean identity) {
            return indexOf(value, identity) >= 0;
        }

        @Override
        public int indexOf(V value) {
            return indexOf(value, false);
        }

        @Override
        public int indexOf(V value, boolean identity) {
            for (int i = 0; i < map.size; i++) {
                if (valuesEqual(value, map.denseValues[i], identity)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(V value) {
            return lastIndexOf(value, false);
        }

        @Override
        public int lastIndexOf(V value, boolean identity) {
            for (int i = map.size - 1; i >= 0; i--) {
                if (valuesEqual(value, map.denseValues[i], identity)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int size() {
            return map.size;
        }

        @Override
        public boolean isEmpty() {
            return map.size == 0;
        }

        @Override
        public boolean notEmpty() {
            return map.size != 0;
        }

        @Override
        public Object[] toArray() {
            return Arrays.copyOf(map.denseValues, map.size);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A> A[] toArray(A[] destination) {
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (destination.length < map.size) {
                return (A[])Arrays.copyOf(map.denseValues, map.size, destination.getClass());
            }
            System.arraycopy(map.denseValues, 0, destination, 0, map.size);
            if (destination.length > map.size) {
                destination[map.size] = null;
            }
            return destination;
        }

        @Override
        public ObjectIterator<V> iterator() {
            if (iterator == null) {
                iterator = new ValueIterator<V>(map, false);
            }
            return iterator.reset();
        }
    }
}
