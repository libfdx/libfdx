package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stores values in a reusable growable array.
 *
 * @param <T> the value type
 * @author xpenatan
 */
public final class FdxArray<T> implements Iterable<T> {
    private Object[] items;
    private int size;
    private boolean ordered;

    /**
     * Creates an ordered array.
     */
    public FdxArray() {
        this(true, 16);
    }

    /**
     * Creates an ordered array.
     *
     * @param capacity the initial capacity
     */
    public FdxArray(int capacity) {
        this(true, capacity);
    }

    /**
     * Creates an array.
     *
     * @param ordered whether removals preserve order
     * @param capacity the initial capacity
     */
    public FdxArray(boolean ordered, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.ordered = ordered;
        this.items = new Object[Math.max(1, capacity)];
    }

    /**
     * Adds a value.
     *
     * @param value the value
     * @return this array
     */
    public FdxArray<T> add(T value) {
        ensureCapacity(1);
        items[size++] = value;
        return this;
    }

    /**
     * Adds all values from another array.
     *
     * @param values the values
     * @return this array
     */
    public FdxArray<T> addAll(FdxArray<? extends T> values) {
        ensureCapacity(values.size);
        for (int i = 0; i < values.size; i++) {
            items[size++] = values.items[i];
        }
        return this;
    }

    /**
     * Inserts a value at an index.
     *
     * @param index the index
     * @param value the value
     * @return this array
     */
    public FdxArray<T> insert(int index, T value) {
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
    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T)items[index];
    }

    /**
     * Sets the value at an index.
     *
     * @param index the index
     * @param value the value
     * @return the previous value
     */
    @SuppressWarnings("unchecked")
    public T set(int index, T value) {
        checkIndex(index);
        Object old = items[index];
        items[index] = value;
        return (T)old;
    }

    /**
     * Returns the first value.
     *
     * @return the first value
     */
    @SuppressWarnings("unchecked")
    public T first() {
        checkNotEmpty();
        return (T)items[0];
    }

    /**
     * Returns the last value without removing it.
     *
     * @return the last value
     */
    @SuppressWarnings("unchecked")
    public T peek() {
        checkNotEmpty();
        return (T)items[size - 1];
    }

    /**
     * Removes and returns the last value.
     *
     * @return the removed value
     */
    @SuppressWarnings("unchecked")
    public T pop() {
        checkNotEmpty();
        int index = --size;
        Object value = items[index];
        items[index] = null;
        return (T)value;
    }

    /**
     * Returns whether this array contains a value.
     *
     * @param value the value
     * @return true if present
     */
    public boolean contains(T value) {
        return contains(value, false);
    }

    /**
     * Returns whether this array contains a value.
     *
     * @param value the value
     * @param identity true to compare by identity
     * @return true if present
     */
    public boolean contains(T value, boolean identity) {
        return indexOf(value, identity) >= 0;
    }

    /**
     * Returns the first index of a value.
     *
     * @param value the value
     * @return the index, or -1
     */
    public int indexOf(T value) {
        return indexOf(value, false);
    }

    /**
     * Returns the first index of a value.
     *
     * @param value the value
     * @param identity true to compare by identity
     * @return the index, or -1
     */
    public int indexOf(T value, boolean identity) {
        return indexOfValue(value, identity);
    }

    /**
     * Returns the last index of a value.
     *
     * @param value the value
     * @return the index, or -1
     */
    public int lastIndexOf(T value) {
        return lastIndexOf(value, false);
    }

    /**
     * Returns the last index of a value.
     *
     * @param value the value
     * @param identity true to compare by identity
     * @return the index, or -1
     */
    public int lastIndexOf(T value, boolean identity) {
        return lastIndexOfValue(value, identity);
    }

    /**
     * Removes the first matching value.
     *
     * @param value the value
     * @return true if removed
     */
    public boolean removeValue(T value) {
        return removeValue(value, false);
    }

    /**
     * Removes the first matching value.
     *
     * @param value the value
     * @param identity true to compare by identity
     * @return true if removed
     */
    public boolean removeValue(T value, boolean identity) {
        int index = indexOf(value, identity);
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
    @SuppressWarnings("unchecked")
    public T removeIndex(int index) {
        checkIndex(index);
        Object old = items[index];
        size--;
        if (ordered) {
            System.arraycopy(items, index + 1, items, index, size - index);
        } else {
            items[index] = items[size];
        }
        items[size] = null;
        return (T)old;
    }

    /**
     * Removes the first matching occurrence for each supplied value.
     *
     * @param values the values to remove
     * @return true if any value was removed
     */
    public boolean removeAll(FdxArray<? extends T> values) {
        return removeAll(values, false);
    }

    /**
     * Removes the first matching occurrence for each supplied value.
     *
     * @param values the values to remove
     * @param identity true to compare by identity
     * @return true if any value was removed
     */
    public boolean removeAll(FdxArray<? extends T> values, boolean identity) {
        if (values == this) {
            boolean changed = size > 0;
            clear();
            return changed;
        }
        boolean changed = false;
        for (int i = 0; i < values.size; i++) {
            int index = indexOfValue(values.items[i], identity);
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
    public FdxArray<T> removeRange(int start, int end) {
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
        Arrays.fill(items, newSize, size, null);
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
    public FdxArray<T> swap(int first, int second) {
        checkIndex(first);
        checkIndex(second);
        Object value = items[first];
        items[first] = items[second];
        items[second] = value;
        return this;
    }

    /**
     * Reverses the values in place.
     *
     * @return this array
     */
    public FdxArray<T> reverse() {
        for (int i = 0, last = size - 1; i < last; i++, last--) {
            Object value = items[i];
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
    public FdxArray<T> truncate(int newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("newSize must be >= 0");
        }
        if (newSize < size) {
            Arrays.fill(items, newSize, size, null);
            size = newSize;
        }
        return this;
    }

    /**
     * Removes all values.
     */
    public void clear() {
        Arrays.fill(items, 0, size, null);
        size = 0;
    }

    /**
     * Ensures additional capacity.
     *
     * @param additionalCapacity the additional capacity
     * @return this array
     */
    public FdxArray<T> ensureCapacity(int additionalCapacity) {
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
    public FdxArray<T> shrink() {
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
    public FdxArray<T> ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    /**
     * Returns the backing values as a copy.
     *
     * @return the values
     */
    public Object[] toArray() {
        return Arrays.copyOf(items, size);
    }

    @Override
    public Iterator<T> iterator() {
        return new ArrayIterator<T>(this);
    }

    private void resize(int capacity) {
        items = Arrays.copyOf(items, capacity);
    }

    private int indexOfValue(Object value, boolean identity) {
        for (int i = 0; i < size; i++) {
            if (valuesEqual(value, items[i], identity)) {
                return i;
            }
        }
        return -1;
    }

    private int lastIndexOfValue(Object value, boolean identity) {
        for (int i = size - 1; i >= 0; i--) {
            if (valuesEqual(value, items[i], identity)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean valuesEqual(Object value, Object other, boolean identity) {
        if (identity) {
            return value == other;
        }
        return value == null ? other == null : value.equals(other);
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

    private static final class ArrayIterator<T> implements Iterator<T> {
        private final FdxArray<T> array;
        private int index;

        ArrayIterator(FdxArray<T> array) {
            this.array = array;
        }

        @Override
        public boolean hasNext() {
            return index < array.size;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return array.get(index++);
        }
    }
}
