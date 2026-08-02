package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stores non-null object values in a compact open-addressed set.
 *
 * <p>Add, membership lookup, and removal are expected constant time.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public final class ObjectSet<T> implements Iterable<T> {
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private Object[] values;
    private byte[] states;
    private int size;
    private int occupied;
    private int threshold;
    private final float loadFactor;

    /**
     * Creates a set.
     */
    public ObjectSet() {
        this(32, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a set.
     *
     * @param capacity the expected capacity
     */
    public ObjectSet(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a set.
     *
     * @param capacity the expected capacity
     * @param loadFactor the load factor
     */
    public ObjectSet(int capacity, float loadFactor) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        CollectionHash.checkLoadFactor(loadFactor);
        this.loadFactor = loadFactor;
        allocate(CollectionHash.tableSize(capacity, loadFactor));
    }

    /**
     * Adds a value.
     *
     * @param value the value
     * @return true if the value was added
     */
    public boolean add(T value) {
        requireValue(value);
        if (occupied == values.length) {
            resize(values.length);
        }
        int index = locate(value);
        if (index >= 0) {
            return false;
        }
        if (size + 1 > threshold) {
            resize(values.length << 1);
            index = locate(value);
        }
        else if (occupied + 1 > threshold) {
            resize(values.length);
            index = locate(value);
        }
        index = -index - 1;
        if (states[index] == CollectionHash.EMPTY) {
            occupied++;
        }
        states[index] = CollectionHash.USED;
        values[index] = value;
        size++;
        return true;
    }

    /**
     * Returns whether a value exists.
     *
     * @param value the value
     * @return true if present
     */
    public boolean contains(T value) {
        requireValue(value);
        return locate(value) >= 0;
    }

    /**
     * Removes a value.
     *
     * @param value the value
     * @return true if the value was removed
     */
    public boolean remove(T value) {
        requireValue(value);
        int index = locate(value);
        if (index < 0) {
            return false;
        }
        states[index] = CollectionHash.REMOVED;
        values[index] = null;
        size--;
        return true;
    }

    /**
     * Removes all values.
     */
    public void clear() {
        Arrays.fill(states, CollectionHash.EMPTY);
        Arrays.fill(values, null);
        size = 0;
        occupied = 0;
    }

    /**
     * Ensures additional value capacity.
     *
     * @param additionalCapacity the additional values to reserve
     * @return this set
     */
    public ObjectSet<T> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > threshold) {
            resize(CollectionHash.tableSize(required, loadFactor));
        }
        else if (occupied + additionalCapacity > threshold) {
            resize(values.length);
        }
        return this;
    }

    /**
     * Shrinks and compacts storage for the current values.
     *
     * @return this set
     */
    public ObjectSet<T> shrink() {
        int tableSize = CollectionHash.tableSize(size, loadFactor);
        if (tableSize < values.length || occupied != size) {
            resize(tableSize);
        }
        return this;
    }

    /**
     * Returns the number of values.
     *
     * @return the number of values
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this set is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this set has at least one value.
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
        return values.length;
    }

    @Override
    public Iterator<T> iterator() {
        return new ValueIterator<T>(this);
    }

    private int locate(T value) {
        int mask = values.length - 1;
        int index = CollectionHash.mix(value.hashCode()) & mask;
        int firstRemoved = -1;
        for (int probes = 0; probes < values.length; probes++) {
            byte state = states[index];
            if (state == CollectionHash.EMPTY) {
                return -(firstRemoved >= 0 ? firstRemoved : index) - 1;
            }
            if (state == CollectionHash.USED && value.equals(values[index])) {
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
        values = new Object[tableSize];
        states = new byte[tableSize];
        threshold = CollectionHash.threshold(tableSize, loadFactor);
        occupied = 0;
    }

    private void resize(int tableSize) {
        Object[] oldValues = values;
        byte[] oldStates = states;
        int oldSize = size;
        allocate(tableSize);
        size = 0;
        for (int i = 0; i < oldValues.length; i++) {
            if (oldStates[i] == CollectionHash.USED) {
                add(valueAt(oldValues, i));
            }
        }
        if (size != oldSize) {
            throw new IllegalStateException("rehash lost values");
        }
    }

    private void requireValue(T value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @SuppressWarnings("unchecked")
    private T valueAt(Object[] source, int index) {
        return (T)source[index];
    }

    private static final class ValueIterator<T> implements Iterator<T> {
        private final ObjectSet<T> set;
        private int nextIndex;
        private int returned;

        ValueIterator(ObjectSet<T> set) {
            this.set = set;
            findNext();
        }

        @Override
        public boolean hasNext() {
            return returned < set.size;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            returned++;
            nextIndex++;
            findNext();
            return set.valueAt(set.values, index);
        }

        private void findNext() {
            while (nextIndex < set.states.length && set.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }
}
