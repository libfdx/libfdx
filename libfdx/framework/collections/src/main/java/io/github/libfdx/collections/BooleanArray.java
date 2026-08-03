package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Stores primitive boolean values in a reusable growable array.
 *
 * <p>Values occupy a packed prefix of a contiguous {@code boolean[]}.
 * Ordered arrays preserve index order on removal; unordered arrays replace a
 * removed value with the last value.</p>
 *
 * @author xpenatan
 */
public final class BooleanArray implements BooleanIterable {
    private boolean[] items;
    private int size;
    private boolean ordered;
    private ArrayIterator iterator;

    /** Creates an ordered array with default capacity. */
    public BooleanArray() {
        this(true, 16);
    }

    /** Creates an ordered array. */
    public BooleanArray(int capacity) {
        this(true, capacity);
    }

    /** Creates an array. */
    public BooleanArray(boolean ordered, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.ordered = ordered;
        items = new boolean[Math.max(1, capacity)];
    }

    /** Adds a value. */
    public BooleanArray add(boolean value) {
        ensureCapacity(1);
        items[size++] = value;
        return this;
    }

    /** Adds all values from another array. */
    public BooleanArray addAll(BooleanArray values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        ensureCapacity(values.size);
        System.arraycopy(values.items, 0, items, size, values.size);
        size += values.size;
        return this;
    }

    /** Adds all supplied values. */
    public BooleanArray addAll(boolean... values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return addAll(values, 0, values.length);
    }

    /** Adds a range of supplied values. */
    public BooleanArray addAll(boolean[] values, int offset, int length) {
        checkSourceRange(values, offset, length);
        ensureCapacity(length);
        System.arraycopy(values, offset, items, size, length);
        size += length;
        return this;
    }

    /** Inserts a value at an index. */
    public BooleanArray insert(int index, boolean value) {
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

    /** Returns the value at an index. */
    public boolean get(int index) {
        checkIndex(index);
        return items[index];
    }

    /** Replaces and returns the value at an index. */
    public boolean set(int index, boolean value) {
        checkIndex(index);
        boolean previous = items[index];
        items[index] = value;
        return previous;
    }

    /** Returns the first value. */
    public boolean first() {
        checkNotEmpty();
        return items[0];
    }

    /** Returns the last value. */
    public boolean peek() {
        checkNotEmpty();
        return items[size - 1];
    }

    /** Removes and returns the last value. */
    public boolean pop() {
        checkNotEmpty();
        return items[--size];
    }

    /** Returns whether the value is present. */
    public boolean contains(boolean value) {
        return indexOf(value) >= 0;
    }

    /** Returns the first index of a value, or -1. */
    public int indexOf(boolean value) {
        for (int i = 0; i < size; i++) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Returns the last index of a value, or -1. */
    public int lastIndexOf(boolean value) {
        for (int i = size - 1; i >= 0; i--) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Removes the first matching value. */
    public boolean removeValue(boolean value) {
        int index = indexOf(value);
        if (index < 0) {
            return false;
        }
        removeIndex(index);
        return true;
    }

    /** Removes and returns the value at an index. */
    public boolean removeIndex(int index) {
        checkIndex(index);
        boolean previous = items[index];
        size--;
        if (ordered) {
            System.arraycopy(items, index + 1, items, index, size - index);
        }
        else {
            items[index] = items[size];
        }
        return previous;
    }

    /** Removes the first occurrence of every supplied value. */
    public boolean removeAll(BooleanArray values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
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

    /** Removes values in an inclusive index range. */
    public BooleanArray removeRange(int start, int end) {
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

    /** Swaps two values. */
    public BooleanArray swap(int first, int second) {
        checkIndex(first);
        checkIndex(second);
        boolean value = items[first];
        items[first] = items[second];
        items[second] = value;
        return this;
    }

    /** Reverses the active values. */
    public BooleanArray reverse() {
        for (int i = 0, last = size - 1; i < last; i++, last--) {
            boolean value = items[i];
            items[i] = items[last];
            items[last] = value;
        }
        return this;
    }

    /** Reduces the size to at most the supplied size. */
    public BooleanArray truncate(int newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("newSize must be >= 0");
        }
        if (newSize < size) {
            size = newSize;
        }
        return this;
    }

    /** Removes all values without shrinking storage. */
    public void clear() {
        size = 0;
    }

    /** Ensures capacity for additional values. */
    public BooleanArray ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > items.length) {
            resize(Math.max(required, items.length + (items.length >> 1) + 1));
        }
        return this;
    }

    /** Shrinks backing storage to the active size. */
    public BooleanArray shrink() {
        if (items.length != size) {
            resize(Math.max(1, size));
        }
        return this;
    }

    /** @return the number of active values */
    public int size() {
        return size;
    }

    /** @return the backing capacity */
    public int capacity() {
        return items.length;
    }

    /** @return true when empty */
    public boolean isEmpty() {
        return size == 0;
    }

    /** @return true when non-empty */
    public boolean notEmpty() {
        return size > 0;
    }

    /** @return whether indexed order is preserved by removal */
    public boolean isOrdered() {
        return ordered;
    }

    /** Changes ordered-removal behavior. */
    public BooleanArray ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    /** Returns a copy of the active values. */
    public boolean[] toArray() {
        return Arrays.copyOf(items, size);
    }

    @Override
    public BooleanIterator iterator() {
        if (iterator == null) {
            iterator = new ArrayIterator(this);
        }
        return iterator.reset();
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
            throw new IndexOutOfBoundsException(
                    "start=" + start + ", end=" + end + ", size=" + size);
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

    private static void checkSourceRange(boolean[] values, int offset, int length) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (offset < 0 || length < 0 || offset > values.length - length) {
            throw new IndexOutOfBoundsException(
                    "offset=" + offset + ", length=" + length
                            + ", arrayLength=" + values.length);
        }
    }

    private static final class ArrayIterator implements BooleanIterator {
        private final BooleanArray array;
        private int index;

        ArrayIterator(BooleanArray array) {
            this.array = array;
        }

        @Override
        public boolean hasNext() {
            return index < array.size;
        }

        @Override
        public boolean nextBoolean() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return array.items[index++];
        }

        @Override
        public BooleanIterator reset() {
            index = 0;
            return this;
        }
    }
}
