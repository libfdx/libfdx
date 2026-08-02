package io.github.libfdx.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stores values in a growable circular queue.
 *
 * <p>Adding and removing values at opposite ends are amortized constant-time
 * operations. Queue storage is reused after values are removed.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public final class ObjectQueue<T> implements Iterable<T> {
    private Object[] items;
    private int head;
    private int size;

    /**
     * Creates a queue.
     */
    public ObjectQueue() {
        this(16);
    }

    /**
     * Creates a queue.
     *
     * @param capacity the initial capacity
     */
    public ObjectQueue(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        items = new Object[Math.max(1, capacity)];
    }

    /**
     * Adds a value at the end.
     *
     * @param value the value
     * @return this queue
     */
    public ObjectQueue<T> addLast(T value) {
        ensureCapacity(1);
        items[physicalIndex(size)] = value;
        size++;
        return this;
    }

    /**
     * Adds a value at the front.
     *
     * @param value the value
     * @return this queue
     */
    public ObjectQueue<T> addFirst(T value) {
        ensureCapacity(1);
        head = (head - 1 + items.length) % items.length;
        items[head] = value;
        size++;
        return this;
    }

    /**
     * Removes and returns the first value.
     *
     * @return the first value
     */
    public T removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException("ObjectQueue is empty");
        }
        return removeFirstValue();
    }

    /**
     * Removes and returns the first value, or null when empty.
     *
     * @return the first value, or null
     */
    public T pollFirst() {
        return size > 0 ? removeFirstValue() : null;
    }

    /**
     * Returns the first value without removing it.
     *
     * @return the first value
     */
    public T first() {
        if (size == 0) {
            throw new NoSuchElementException("ObjectQueue is empty");
        }
        return valueAt(head);
    }

    /**
     * Returns the value at a queue-relative index.
     *
     * @param index the index
     * @return the value
     */
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
        return valueAt(physicalIndex(index));
    }

    /**
     * Removes all values while retaining storage.
     */
    public void clear() {
        for (int i = 0; i < size; i++) {
            items[physicalIndex(i)] = null;
        }
        head = 0;
        size = 0;
    }

    /**
     * Ensures additional queue capacity.
     *
     * @param additionalCapacity the additional values to reserve
     * @return this queue
     */
    public ObjectQueue<T> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > items.length) {
            resize(Math.max(required, Math.max(8, items.length + (items.length >> 1))));
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
     * Returns whether this queue is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this queue has at least one value.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return size > 0;
    }

    /**
     * Returns the current storage capacity.
     *
     * @return the current capacity
     */
    public int capacity() {
        return items.length;
    }

    @Override
    public Iterator<T> iterator() {
        return new QueueIterator<T>(this);
    }

    @SuppressWarnings("unchecked")
    private T removeFirstValue() {
        int index = head;
        Object value = items[index];
        items[index] = null;
        head = (head + 1) % items.length;
        size--;
        if (size == 0) {
            head = 0;
        }
        return (T)value;
    }

    private int physicalIndex(int relativeIndex) {
        int index = head + relativeIndex;
        return index >= items.length ? index % items.length : index;
    }

    @SuppressWarnings("unchecked")
    private T valueAt(int index) {
        return (T)items[index];
    }

    private void resize(int capacity) {
        Object[] replacement = new Object[capacity];
        for (int i = 0; i < size; i++) {
            replacement[i] = items[physicalIndex(i)];
        }
        items = replacement;
        head = 0;
    }

    private static final class QueueIterator<T> implements Iterator<T> {
        private final ObjectQueue<T> queue;
        private int index;

        QueueIterator(ObjectQueue<T> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            return index < queue.size;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return queue.get(index++);
        }
    }
}
