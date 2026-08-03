package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Maps primitive long keys to values with stable ordered traversal and packed
 * dense indexed storage, without boxing or per-entry nodes.
 *
 * <p><b>Algorithm:</b> A power-of-two open-addressed hash table uses linear
 * probing and tombstones to map each primitive key to a dense index. Primitive
 * inverse indices map dense entries back to their table slots. Keys and values
 * occupy packed arrays, while primitive previous/next dense-index arrays
 * maintain an independent logical sequence. Removal unlinks the entry, swaps
 * the dense tail into its slot, and repairs both mappings and order links.</p>
 *
 * <p><b>Ordering:</b> {@link #entries()}, {@link #keys()}, and
 * {@link #values()} follow insertion order, as modified by
 * {@link #moveToFirst(long)} and {@link #moveToLast(long)}. Replacing a value
 * retains its position; removing and reinserting a key appends it. Dense access
 * through {@link #keyAt(int)} and {@link #valueAt(int)} is unordered and may
 * change after removal.</p>
 *
 * <p><b>Performance:</b> Put is expected amortized {@code O(1)}, and get,
 * contains-key, removal, and order moves are expected {@code O(1)} with
 * well-distributed keys. Collision-heavy or resize worst cases are
 * {@code O(n)}. Dense indexed access is {@code O(1)}. Ordered and dense full
 * traversal are {@code O(n)}; dense traversal is contiguous.</p>
 *
 * <p><b>Allocation:</b> Mutations reuse primitive and object arrays until
 * growth. Iterable views, iterators, the read-only view, and the mutable entry
 * returned during iteration are cached after first use. Nested or concurrent
 * iteration over the same view is unsupported.</p>
 *
 * @param <V> the value type
 * @author xpenatan
 */
public final class OrderedLongMap<V> implements LongMapView<V> {
    private static final int DEFAULT_CAPACITY = 32;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private long[] tableKeys;
    private int[] tableDenseIndices;
    private byte[] tableStates;
    private long[] denseKeys;
    private Object[] denseValues;
    private int[] denseTableSlots;
    private int[] previousDense;
    private int[] nextDense;
    private int size;
    private int occupied;
    private int threshold;
    private int first = -1;
    private int last = -1;
    private final float loadFactor;
    private Entries<V> entries;
    private Keys keysView;
    private Values<V> valuesView;
    private LongMapView<V> view;

    /** Creates an ordered primitive-long map. */
    public OrderedLongMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates an ordered primitive-long map.
     *
     * @param capacity expected entry capacity
     */
    public OrderedLongMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates an ordered map containing the supplied entries in their
     * traversal order.
     *
     * @param values source entries, or null for an empty map
     */
    public OrderedLongMap(LongMapView<? extends V> values) {
        this(values != null ? values.size() : 0, DEFAULT_LOAD_FACTOR);
        if (values != null) {
            putAll(values);
        }
    }

    /**
     * Creates an ordered primitive-long map.
     *
     * @param capacity expected entry capacity
     * @param loadFactor hash-table load factor
     */
    public OrderedLongMap(int capacity, float loadFactor) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        CollectionHash.checkLoadFactor(loadFactor);
        this.loadFactor = loadFactor;
        allocateTable(CollectionHash.tableSize(capacity, loadFactor));
        allocateDense(capacity);
    }

    /**
     * Adds or replaces a value. Replacement retains logical order.
     *
     * @param key the full-range primitive key
     * @param value the value
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        if (occupied == tableKeys.length) {
            resizeTable(tableKeys.length);
        }
        int tableSlot = locate(key);
        if (tableSlot >= 0) {
            int denseIndex = tableDenseIndices[tableSlot];
            V previous = (V)denseValues[denseIndex];
            denseValues[denseIndex] = value;
            return previous;
        }
        if (size + 1 > threshold) {
            resizeTable(tableKeys.length << 1);
            tableSlot = locate(key);
        }
        else if (occupied + 1 > threshold) {
            resizeTable(tableKeys.length);
            tableSlot = locate(key);
        }

        ensureDenseAddress(size + 1);
        tableSlot = -tableSlot - 1;
        if (tableStates[tableSlot] == CollectionHash.EMPTY) {
            occupied++;
        }
        int denseIndex = size;
        tableStates[tableSlot] = CollectionHash.USED;
        tableKeys[tableSlot] = key;
        tableDenseIndices[tableSlot] = denseIndex;
        denseKeys[denseIndex] = key;
        denseValues[denseIndex] = value;
        denseTableSlots[denseIndex] = tableSlot;
        linkLast(denseIndex);
        size++;
        return null;
    }

    /**
     * Adds or replaces entries in the source view's traversal order.
     *
     * @param source source entries
     * @return this map
     */
    public OrderedLongMap<V> putAll(LongMapView<? extends V> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        ensureCapacity(source.size());
        ObjectIterator<? extends LongMapEntry<? extends V>> iterator =
                source.entries().iterator();
        while (iterator.hasNext()) {
            LongMapEntry<? extends V> entry = iterator.next();
            put(entry.key(), entry.value());
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public V get(long key) {
        int tableSlot = locate(key);
        return tableSlot >= 0 ? valueAtUnchecked(tableDenseIndices[tableSlot]) : null;
    }

    /** {@inheritDoc} */
    @Override
    public V get(long key, V defaultValue) {
        int tableSlot = locate(key);
        return tableSlot >= 0
                ? valueAtUnchecked(tableDenseIndices[tableSlot]) : defaultValue;
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsKey(long key) {
        return locate(key) >= 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsValue(V value) {
        return containsValue(value, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsValue(V value, boolean identity) {
        for (int i = 0; i < size; i++) {
            if (valuesEqual(value, denseValues[i], identity)) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public long findKey(V value, long defaultKey) {
        return findKey(value, false, defaultKey);
    }

    /** {@inheritDoc} */
    @Override
    public long findKey(V value, boolean identity, long defaultKey) {
        for (int denseIndex = first; denseIndex >= 0;
                denseIndex = nextDense[denseIndex]) {
            if (valuesEqual(value, denseValues[denseIndex], identity)) {
                return denseKeys[denseIndex];
            }
        }
        return defaultKey;
    }

    /**
     * Removes a key and repairs packed dense storage and logical order.
     *
     * @param key the key
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int tableSlot = locate(key);
        if (tableSlot < 0) {
            return null;
        }
        int denseIndex = tableDenseIndices[tableSlot];
        V previous = (V)denseValues[denseIndex];
        unlink(denseIndex);
        tableStates[tableSlot] = CollectionHash.REMOVED;

        int lastDenseIndex = --size;
        if (denseIndex != lastDenseIndex) {
            moveDenseEntry(lastDenseIndex, denseIndex);
        }
        denseValues[lastDenseIndex] = null;
        return previous;
    }

    /**
     * Moves an existing key to the beginning of logical traversal.
     *
     * @param key the existing key
     * @return this map
     */
    public OrderedLongMap<V> moveToFirst(long key) {
        int denseIndex = requireDenseIndex(key);
        if (denseIndex == first) {
            return this;
        }
        unlink(denseIndex);
        previousDense[denseIndex] = -1;
        nextDense[denseIndex] = first;
        previousDense[first] = denseIndex;
        first = denseIndex;
        return this;
    }

    /**
     * Moves an existing key to the end of logical traversal.
     *
     * @param key the existing key
     * @return this map
     */
    public OrderedLongMap<V> moveToLast(long key) {
        int denseIndex = requireDenseIndex(key);
        if (denseIndex == last) {
            return this;
        }
        unlink(denseIndex);
        previousDense[denseIndex] = last;
        nextDense[denseIndex] = -1;
        nextDense[last] = denseIndex;
        last = denseIndex;
        return this;
    }

    /** Removes all entries while retaining table and dense storage. */
    public void clear() {
        Arrays.fill(tableStates, CollectionHash.EMPTY);
        Arrays.fill(denseValues, 0, size, null);
        size = 0;
        occupied = 0;
        first = -1;
        last = -1;
    }

    /**
     * Ensures additional entries can be inserted without table or dense-array
     * growth.
     *
     * @param additionalCapacity additional entries to reserve
     * @return this map
     */
    public OrderedLongMap<V> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        long required = (long)size + additionalCapacity;
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("required capacity is too large");
        }
        if (required > threshold) {
            resizeTable(CollectionHash.tableSize((int)required, loadFactor));
        }
        else if ((long)occupied + additionalCapacity > threshold) {
            resizeTable(tableKeys.length);
        }
        if (required > denseKeys.length) {
            resizeDense((int)required);
        }
        return this;
    }

    /**
     * Shrinks and compacts table and dense storage for the current entries.
     *
     * @return this map
     */
    public OrderedLongMap<V> shrink() {
        int tableSize = CollectionHash.tableSize(size, loadFactor);
        if (tableSize < tableKeys.length || occupied != size) {
            resizeTable(tableSize);
        }
        if (denseKeys.length != size) {
            resizeDense(size);
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return size;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean notEmpty() {
        return size != 0;
    }

    /** @return the current hash-table capacity */
    public int capacity() {
        return tableKeys.length;
    }

    /** @return the current packed dense-entry capacity */
    public int denseCapacity() {
        return denseKeys.length;
    }

    /**
     * Returns the first key in logical order.
     *
     * @return the first key
     * @throws NoSuchElementException when empty
     */
    public long firstKey() {
        if (first < 0) {
            throw new NoSuchElementException("OrderedLongMap is empty");
        }
        return denseKeys[first];
    }

    /**
     * Returns the last key in logical order.
     *
     * @return the last key
     * @throws NoSuchElementException when empty
     */
    public long lastKey() {
        if (last < 0) {
            throw new NoSuchElementException("OrderedLongMap is empty");
        }
        return denseKeys[last];
    }

    /**
     * Returns the key at a packed dense unordered index.
     *
     * @param index dense index
     * @return the key
     */
    public long keyAt(int index) {
        checkDenseIndex(index);
        return denseKeys[index];
    }

    /**
     * Returns the value at a packed dense unordered index.
     *
     * @param index dense index
     * @return the value
     */
    public V valueAt(int index) {
        checkDenseIndex(index);
        return valueAtUnchecked(index);
    }

    /** {@inheritDoc} */
    @Override
    public ObjectIterable<Entry<V>> entries() {
        if (entries == null) {
            entries = new Entries<V>(this);
        }
        return entries;
    }

    /** {@inheritDoc} */
    @Override
    public LongIterable keys() {
        if (keysView == null) {
            keysView = new Keys(this);
        }
        return keysView;
    }

    /** {@inheritDoc} */
    @Override
    public ObjectIterable<V> values() {
        if (valuesView == null) {
            valuesView = new Values<V>(this);
        }
        return valuesView;
    }

    /**
     * Returns a cached read-only live view.
     *
     * @return the read-only view
     */
    public LongMapView<V> view() {
        if (view == null) {
            view = new ReadOnlyView<V>(this);
        }
        return view;
    }

    private int locate(long key) {
        int mask = tableKeys.length - 1;
        int tableSlot = CollectionHash.mix(key) & mask;
        int firstRemoved = -1;
        for (int probes = 0; probes < tableKeys.length; probes++) {
            byte state = tableStates[tableSlot];
            if (state == CollectionHash.EMPTY) {
                return -(firstRemoved >= 0 ? firstRemoved : tableSlot) - 1;
            }
            if (state == CollectionHash.USED && tableKeys[tableSlot] == key) {
                return tableSlot;
            }
            if (state == CollectionHash.REMOVED && firstRemoved < 0) {
                firstRemoved = tableSlot;
            }
            tableSlot = (tableSlot + 1) & mask;
        }
        return -(firstRemoved >= 0 ? firstRemoved : 0) - 1;
    }

    private void allocateTable(int tableSize) {
        tableKeys = new long[tableSize];
        tableDenseIndices = new int[tableSize];
        tableStates = new byte[tableSize];
        threshold = CollectionHash.threshold(tableSize, loadFactor);
        occupied = 0;
    }

    private void allocateDense(int capacity) {
        denseKeys = new long[capacity];
        denseValues = new Object[capacity];
        denseTableSlots = new int[capacity];
        previousDense = new int[capacity];
        nextDense = new int[capacity];
    }

    private void resizeTable(int tableSize) {
        allocateTable(tableSize);
        for (int denseIndex = 0; denseIndex < size; denseIndex++) {
            int tableSlot = -locate(denseKeys[denseIndex]) - 1;
            tableStates[tableSlot] = CollectionHash.USED;
            tableKeys[tableSlot] = denseKeys[denseIndex];
            tableDenseIndices[tableSlot] = denseIndex;
            denseTableSlots[denseIndex] = tableSlot;
            occupied++;
        }
    }

    private void ensureDenseAddress(int required) {
        if (required <= denseKeys.length) {
            return;
        }
        long grown = Math.max(8L,
                (long)denseKeys.length + (denseKeys.length >> 1) + 1L);
        resizeDense((int)Math.min(Integer.MAX_VALUE, Math.max(grown, required)));
    }

    private void resizeDense(int capacity) {
        denseKeys = Arrays.copyOf(denseKeys, capacity);
        denseValues = Arrays.copyOf(denseValues, capacity);
        denseTableSlots = Arrays.copyOf(denseTableSlots, capacity);
        previousDense = Arrays.copyOf(previousDense, capacity);
        nextDense = Arrays.copyOf(nextDense, capacity);
    }

    private void moveDenseEntry(int source, int destination) {
        int movedTableSlot = denseTableSlots[source];
        int before = previousDense[source];
        int after = nextDense[source];
        denseKeys[destination] = denseKeys[source];
        denseValues[destination] = denseValues[source];
        denseTableSlots[destination] = movedTableSlot;
        previousDense[destination] = before;
        nextDense[destination] = after;
        tableDenseIndices[movedTableSlot] = destination;
        if (before >= 0) {
            nextDense[before] = destination;
        }
        else {
            first = destination;
        }
        if (after >= 0) {
            previousDense[after] = destination;
        }
        else {
            last = destination;
        }
    }

    private void linkLast(int denseIndex) {
        previousDense[denseIndex] = last;
        nextDense[denseIndex] = -1;
        if (last >= 0) {
            nextDense[last] = denseIndex;
        }
        else {
            first = denseIndex;
        }
        last = denseIndex;
    }

    private void unlink(int denseIndex) {
        int before = previousDense[denseIndex];
        int after = nextDense[denseIndex];
        if (before >= 0) {
            nextDense[before] = after;
        }
        else {
            first = after;
        }
        if (after >= 0) {
            previousDense[after] = before;
        }
        else {
            last = before;
        }
    }

    private int requireDenseIndex(long key) {
        int tableSlot = locate(key);
        if (tableSlot < 0) {
            throw new IllegalArgumentException("key is not present: " + key);
        }
        return tableDenseIndices[tableSlot];
    }

    private void checkDenseIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    @SuppressWarnings("unchecked")
    private V valueAtUnchecked(int index) {
        return (V)denseValues[index];
    }

    private static boolean valuesEqual(Object value, Object other, boolean identity) {
        if (identity) {
            return value == other;
        }
        return value == null ? other == null : value.equals(other);
    }

    /** Mutable entry reused by ordered entry iteration. */
    public static final class Entry<V> implements LongMapEntry<V> {
        private long key;
        private V value;

        private Entry() {
        }

        /** {@inheritDoc} */
        @Override
        public long key() {
            return key;
        }

        /** {@inheritDoc} */
        @Override
        public V value() {
            return value;
        }
    }

    private static final class Entries<V> implements ObjectIterable<Entry<V>> {
        private final OrderedLongMap<V> map;
        private EntryIterator<V> iterator;

        Entries(OrderedLongMap<V> map) {
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
        private final OrderedLongMap<V> map;
        private final Entry<V> entry = new Entry<V>();
        private int nextIndex;

        EntryIterator(OrderedLongMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<Entry<V>> reset() {
            entry.value = null;
            nextIndex = map.first;
            return this;
        }

        @Override
        public boolean hasNext() {
            if (nextIndex < 0) {
                entry.value = null;
                return false;
            }
            return true;
        }

        @Override
        public Entry<V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int denseIndex = nextIndex;
            nextIndex = map.nextDense[denseIndex];
            entry.key = map.denseKeys[denseIndex];
            entry.value = map.valueAtUnchecked(denseIndex);
            return entry;
        }
    }

    private static final class Keys implements LongIterable {
        private final OrderedLongMap<?> map;
        private KeyIterator iterator;

        Keys(OrderedLongMap<?> map) {
            this.map = map;
        }

        @Override
        public LongIterator iterator() {
            if (iterator == null) {
                iterator = new KeyIterator(map);
            }
            return iterator.reset();
        }
    }

    private static final class KeyIterator implements LongIterator {
        private final OrderedLongMap<?> map;
        private int nextIndex;

        KeyIterator(OrderedLongMap<?> map) {
            this.map = map;
        }

        @Override
        public LongIterator reset() {
            nextIndex = map.first;
            return this;
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

        @Override
        public long nextLong() {
            if (nextIndex < 0) {
                throw new NoSuchElementException();
            }
            int denseIndex = nextIndex;
            nextIndex = map.nextDense[denseIndex];
            return map.denseKeys[denseIndex];
        }
    }

    private static final class Values<V> implements ObjectIterable<V> {
        private final OrderedLongMap<V> map;
        private ValueIterator<V> iterator;

        Values(OrderedLongMap<V> map) {
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
        private final OrderedLongMap<V> map;
        private int nextIndex;

        ValueIterator(OrderedLongMap<V> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<V> reset() {
            nextIndex = map.first;
            return this;
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

        @Override
        public V next() {
            if (nextIndex < 0) {
                throw new NoSuchElementException();
            }
            int denseIndex = nextIndex;
            nextIndex = map.nextDense[denseIndex];
            return map.valueAtUnchecked(denseIndex);
        }
    }

    private static final class ReadOnlyView<V> implements LongMapView<V> {
        private final OrderedLongMap<V> map;

        ReadOnlyView(OrderedLongMap<V> map) {
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
