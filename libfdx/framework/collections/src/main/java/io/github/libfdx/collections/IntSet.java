package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Stores primitive int values in a compact open-addressed set.
 *
 * <p><b>Algorithm:</b> A power-of-two hash table uses linear probing and
 * tombstones without boxing values.</p>
 *
 * <p><b>Ordering:</b> Unordered. Iteration follows occupied table slots; it
 * does not preserve insertion order and may change after mutation or
 * resizing.</p>
 *
 * <p><b>Performance:</b> Add is expected amortized {@code O(1)}, and
 * membership lookup and removal are expected {@code O(1)} with
 * well-distributed values. Collision-heavy worst cases are {@code O(n)}. Full
 * iteration scans the backing table and is {@code O(table capacity)}. Use
 * {@link IntIterator#nextInt()} in hot loops to avoid boxing.</p>
 *
 * @author xpenatan
 */
public final class IntSet implements IntIterable {
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private int[] keys;
    private byte[] states;
    private int size;
    private int occupied;
    private int threshold;
    private final float loadFactor;
    private SetIterator iterator;

    /**
     * Creates a set.
     */
    public IntSet() {
        this(32, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a set.
     *
     * @param capacity the expected capacity
     */
    public IntSet(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a set.
     *
     * @param capacity the expected capacity
     * @param loadFactor the load factor
     */
    public IntSet(int capacity, float loadFactor) {
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
    public boolean add(int value) {
        if (occupied == keys.length) {
            resize(keys.length);
        }
        int index = locate(value);
        if (index >= 0) {
            return false;
        }
        if (size + 1 > threshold) {
            resize(keys.length << 1);
            index = locate(value);
        }
        else if (occupied + 1 > threshold) {
            resize(keys.length);
            index = locate(value);
        }
        index = -index - 1;
        if (states[index] == CollectionHash.EMPTY) {
            occupied++;
        }
        states[index] = CollectionHash.USED;
        keys[index] = value;
        size++;
        return true;
    }

    /**
     * Returns whether a value exists.
     *
     * @param value the value
     * @return true if present
     */
    public boolean contains(int value) {
        return locate(value) >= 0;
    }

    /**
     * Removes a value.
     *
     * @param value the value
     * @return true if the value was removed
     */
    public boolean remove(int value) {
        int index = locate(value);
        if (index < 0) {
            return false;
        }
        states[index] = CollectionHash.REMOVED;
        size--;
        return true;
    }

    /**
     * Removes all values.
     */
    public void clear() {
        Arrays.fill(states, CollectionHash.EMPTY);
        size = 0;
        occupied = 0;
    }

    /**
     * Ensures additional value capacity.
     *
     * @param additionalCapacity the additional values to reserve
     * @return this set
     */
    public IntSet ensureCapacity(int additionalCapacity) {
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
     * Shrinks and compacts storage for the current values.
     *
     * @return this set
     */
    public IntSet shrink() {
        int tableSize = CollectionHash.tableSize(size, loadFactor);
        if (tableSize < keys.length || occupied != size) {
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
        return keys.length;
    }

    @Override
    public IntIterator iterator() {
        if (iterator == null) {
            iterator = new SetIterator(this);
        }
        return iterator.reset();
    }

    private int locate(int key) {
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
        keys = new int[tableSize];
        states = new byte[tableSize];
        threshold = CollectionHash.threshold(tableSize, loadFactor);
        occupied = 0;
    }

    private void resize(int tableSize) {
        int[] oldKeys = keys;
        byte[] oldStates = states;
        int oldSize = size;
        allocate(tableSize);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStates[i] == CollectionHash.USED) {
                add(oldKeys[i]);
            }
        }
        if (size != oldSize) {
            throw new IllegalStateException("rehash lost values");
        }
    }

    /**
     * Iterates primitive int values.
     *
     * @author xpenatan
     */
    private static final class SetIterator implements IntIterator {
        private final IntSet set;
        private int nextIndex;
        private int returned;

        SetIterator(IntSet set) {
            this.set = set;
        }

        @Override
        public IntIterator reset() {
            nextIndex = 0;
            returned = 0;
            findNext();
            return this;
        }

        @Override
        public boolean hasNext() {
            return returned < set.size;
        }

        /**
         * Returns the next primitive value.
         *
         * @return the next value
         */
        public int nextInt() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int index = nextIndex;
            returned++;
            nextIndex++;
            findNext();
            return set.keys[index];
        }

        private void findNext() {
            while (nextIndex < set.states.length && set.states[nextIndex] != CollectionHash.USED) {
                nextIndex++;
            }
        }
    }
}
