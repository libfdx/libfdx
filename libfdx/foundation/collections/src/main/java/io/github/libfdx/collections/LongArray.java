package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Stores primitive long values in a reusable growable array.
 *
 * @author xpenatan
 */
public final class LongArray {
    private long[] items;
    private int size;
    private boolean ordered;

    /**
     * Creates an ordered array.
     */
    public LongArray() {
        this(true, 16);
    }

    /**
     * Creates an ordered array.
     *
     * @param capacity the initial capacity
     */
    public LongArray(int capacity) {
        this(true, capacity);
    }

    /**
     * Creates an array.
     *
     * @param ordered whether removals preserve order
     * @param capacity the initial capacity
     */
    public LongArray(boolean ordered, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.ordered = ordered;
        this.items = new long[Math.max(1, capacity)];
    }

    /**
     * Adds a value.
     *
     * @param value the value
     * @return this array
     */
    public LongArray add(long value) {
        ensureCapacity(1);
        items[size++] = value;
        return this;
    }

    /**
     * Adds all values.
     *
     * @param values the values
     * @return this array
     */
    public LongArray addAll(LongArray values) {
        ensureCapacity(values.size);
        System.arraycopy(values.items, 0, items, size, values.size);
        size += values.size;
        return this;
    }

    /**
     * Inserts a value at an index.
     *
     * @param index the index
     * @param value the value
     * @return this array
     */
    public LongArray insert(int index, long value) {
        checkInsertIndex(index);
        ensureCapacity(1);
        if (ordered) {
            System.arraycopy(items, index, items, index + 1, size - index);
        }
        else if (index < size) {
            items[size] = items[index];
        }
        items[index] = value;
        size++;
        return this;
    }

    /**
     * Returns the value at an index.
     *
     * @param index the index
     * @return the value
     */
    public long get(int index) {
        checkIndex(index);
        return items[index];
    }

    /**
     * Sets a value.
     *
     * @param index the index
     * @param value the value
     * @return the previous value
     */
    public long set(int index, long value) {
        checkIndex(index);
        long old = items[index];
        items[index] = value;
        return old;
    }

    /**
     * Returns the first value.
     *
     * @return the first value
     */
    public long first() {
        checkNotEmpty();
        return items[0];
    }

    /**
     * Returns the last value without removing it.
     *
     * @return the last value
     */
    public long peek() {
        checkNotEmpty();
        return items[size - 1];
    }

    /**
     * Removes and returns the last value.
     *
     * @return the removed value
     */
    public long pop() {
        checkNotEmpty();
        return items[--size];
    }

    /**
     * Returns whether this array contains a value.
     *
     * @param value the value
     * @return true if present
     */
    public boolean contains(long value) {
        return indexOf(value) >= 0;
    }

    /**
     * Returns the first index of a value.
     *
     * @param value the value
     * @return the index, or -1
     */
    public int indexOf(long value) {
        for (int i = 0; i < size; i++) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the last index of a value.
     *
     * @param value the value
     * @return the index, or -1
     */
    public int lastIndexOf(long value) {
        for (int i = size - 1; i >= 0; i--) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes the first matching value.
     *
     * @param value the value
     * @return true if removed
     */
    public boolean removeValue(long value) {
        int index = indexOf(value);
        if (index < 0) {
            return false;
        }
        removeIndex(index);
        return true;
    }

    /**
     * Removes the value at an index.
     *
     * @param index the index
     * @return the removed value
     */
    public long removeIndex(int index) {
        checkIndex(index);
        long old = items[index];
        size--;
        if (ordered) {
            System.arraycopy(items, index + 1, items, index, size - index);
        } else {
            items[index] = items[size];
        }
        return old;
    }

    /**
     * Removes the first matching occurrence for each supplied value.
     *
     * @param values the values to remove
     * @return true if any value was removed
     */
    public boolean removeAll(LongArray values) {
        if (values == this) {
            boolean changed = size > 0;
            clear();
            return changed;
        }
        boolean changed = false;
        for (int i = 0; i < values.size; i++) {
            int index = indexOf(values.items[i]);
            if (index >= 0) {
                removeIndex(index);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Removes values in the inclusive index range.
     *
     * @param start the first index to remove
     * @param end the last index to remove
     * @return this array
     */
    public LongArray removeRange(int start, int end) {
        checkRange(start, end);
        int count = end - start + 1;
        int newSize = size - count;
        if (ordered) {
            System.arraycopy(items, end + 1, items, start, size - end - 1);
        }
        else {
            int copyStart = Math.max(newSize, end + 1);
            System.arraycopy(items, copyStart, items, start, size - copyStart);
        }
        size = newSize;
        return this;
    }

    /**
     * Swaps two values.
     *
     * @param first the first index
     * @param second the second index
     * @return this array
     */
    public LongArray swap(int first, int second) {
        checkIndex(first);
        checkIndex(second);
        long value = items[first];
        items[first] = items[second];
        items[second] = value;
        return this;
    }

    /**
     * Reverses the values in place.
     *
     * @return this array
     */
    public LongArray reverse() {
        for (int i = 0, last = size - 1; i < last; i++, last--) {
            long value = items[i];
            items[i] = items[last];
            items[last] = value;
        }
        return this;
    }

    /**
     * Reduces the size to at most the requested size.
     *
     * @param newSize the maximum size
     * @return this array
     */
    public LongArray truncate(int newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("newSize must be >= 0");
        }
        if (newSize < size) {
            size = newSize;
        }
        return this;
    }

    /**
     * Removes all values.
     */
    public void clear() {
        size = 0;
    }

    /**
     * Ensures additional capacity.
     *
     * @param additionalCapacity the additional capacity
     * @return this array
     */
    public LongArray ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > items.length) {
            resize(Math.max(required, items.length + (items.length >> 1) + 1));
        }
        return this;
    }

    /**
     * Shrinks storage to the current size.
     *
     * @return this array
     */
    public LongArray shrink() {
        if (items.length != size) {
            resize(Math.max(1, size));
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
     * Returns the current backing capacity.
     *
     * @return the backing capacity
     */
    public int capacity() {
        return items.length;
    }

    /**
     * Returns whether this array is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this array has at least one value.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return size > 0;
    }

    /**
     * Returns whether removals preserve order.
     *
     * @return true if ordered
     */
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Sets whether removals preserve order.
     *
     * @param ordered whether removals preserve order
     * @return this array
     */
    public LongArray ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    /**
     * Returns the values as a copy.
     *
     * @return the values
     */
    public long[] toArray() {
        return Arrays.copyOf(items, size);
    }

    private void resize(int capacity) {
        items = Arrays.copyOf(items, capacity);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void checkRange(int start, int end) {
        if (start < 0 || end < start || end >= size) {
            throw new IndexOutOfBoundsException("start=" + start + ", end=" + end + ", size=" + size);
        }
    }

    private void checkInsertIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void checkNotEmpty() {
        if (size == 0) {
            throw new NoSuchElementException("Array is empty");
        }
    }
}
