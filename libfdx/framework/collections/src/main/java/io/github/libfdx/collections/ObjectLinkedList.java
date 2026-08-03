package io.github.libfdx.collections;

import java.util.NoSuchElementException;

/**
 * Stores values in a doubly linked list.
 *
 * <p><b>Algorithm:</b> Values occupy doubly linked nodes. Removed nodes are
 * retained in a per-list pool; adds reuse them and allocate only when the list
 * exceeds its retained node capacity.</p>
 *
 * <p><b>Ordering:</b> Ordered by the linked sequence from first to last.
 * Adding or removing at either end preserves the relative order of remaining
 * values.</p>
 *
 * <p><b>Performance:</b> Adding at either end is amortized {@code O(1)}.
 * Reading or removing either end, and removing a retained active
 * {@link Node}, are {@code O(1)}. Iteration and clear are {@code O(n)}. The
 * list intentionally provides no indexed random access.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public final class ObjectLinkedList<T> implements ObjectIterable<T> {
    private static final int DEFAULT_CAPACITY = 16;
    private Node<T> first;
    private Node<T> last;
    private Node<T> free;
    private int size;
    private int capacity;
    private ListIterator<T> iterator;

    /**
     * Creates a linked list.
     */
    public ObjectLinkedList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a linked list with enough pooled nodes for the requested number
     * of values.
     *
     * @param capacity the initial node capacity
     */
    public ObjectLinkedList(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        addFreeNodes(capacity);
    }

    /**
     * Adds a value at the front.
     *
     * <p>The returned node is valid only until it is removed or this list is
     * cleared. Removed nodes are pooled and may be reused by a later add.</p>
     *
     * @param value the value
     * @return the active node
     */
    public Node<T> addFirst(T value) {
        Node<T> node = obtain(value);
        Node<T> oldFirst = first;
        first = node;
        node.next = oldFirst;
        if (oldFirst != null) {
            oldFirst.previous = node;
        } else {
            last = node;
        }
        size++;
        return node;
    }

    /**
     * Adds a value at the end.
     *
     * <p>The returned node is valid only until it is removed or this list is
     * cleared. Removed nodes are pooled and may be reused by a later add.</p>
     *
     * @param value the value
     * @return the active node
     */
    public Node<T> addLast(T value) {
        Node<T> node = obtain(value);
        Node<T> oldLast = last;
        last = node;
        node.previous = oldLast;
        if (oldLast != null) {
            oldLast.next = node;
        } else {
            first = node;
        }
        size++;
        return node;
    }

    /**
     * Removes the first value.
     *
     * @return the removed value
     */
    public T removeFirst() {
        if (first == null) {
            throw new NoSuchElementException();
        }
        return remove(first);
    }

    /**
     * Removes the last value.
     *
     * @return the removed value
     */
    public T removeLast() {
        if (last == null) {
            throw new NoSuchElementException();
        }
        return remove(last);
    }

    /**
     * Removes a node from this list.
     *
     * <p>The node reference becomes invalid after this call and must not be
     * retained because its storage may be reused by a later add.</p>
     *
     * @param node the active node
     * @return the removed value
     */
    public T remove(Node<T> node) {
        if (node == null || node.owner != this) {
            throw new IllegalArgumentException("node does not belong to this list");
        }
        Node<T> previous = node.previous;
        Node<T> next = node.next;
        if (previous != null) {
            previous.next = next;
        } else {
            first = next;
        }
        if (next != null) {
            next.previous = previous;
        } else {
            last = previous;
        }
        T value = node.value;
        size--;
        release(node);
        return value;
    }

    /**
     * Removes all values and retains their nodes for reuse.
     */
    public void clear() {
        Node<T> node = first;
        while (node != null) {
            Node<T> next = node.next;
            release(node);
            node = next;
        }
        first = null;
        last = null;
        size = 0;
    }

    /**
     * Returns the first node.
     *
     * @return the first node, or null
     */
    public Node<T> firstNode() {
        return first;
    }

    /**
     * Returns the last node.
     *
     * @return the last node, or null
     */
    public Node<T> lastNode() {
        return last;
    }

    /**
     * Returns the first value.
     *
     * @return the first value, or null
     */
    public T first() {
        return first != null ? first.value : null;
    }

    /**
     * Returns the last value.
     *
     * @return the last value, or null
     */
    public T last() {
        return last != null ? last.value : null;
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
     * Returns whether this list is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this list has at least one value.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return size > 0;
    }

    /**
     * Returns the number of allocated nodes retained by this list.
     *
     * @return the active and pooled node capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Ensures that the requested number of additional values can be added
     * without allocating nodes.
     *
     * @param additionalCapacity the additional values to reserve
     * @return this list
     */
    public ObjectLinkedList<T> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size + additionalCapacity;
        if (required > capacity) {
            addFreeNodes(required - capacity);
        }
        return this;
    }

    @Override
    public ObjectIterator<T> iterator() {
        if (iterator == null) {
            iterator = new ListIterator<T>(this);
        }
        return iterator.reset();
    }

    /**
     * Represents an active linked list node.
     *
     * <p>A node is valid only while it belongs to a list. Removal and
     * {@link ObjectLinkedList#clear()} return it to an internal pool, after
     * which the same node instance may represent another value. Callers must
     * discard node references immediately after removal.</p>
     *
     * @param <T> the value type
     * @author xpenatan
     */
    public static final class Node<T> {
        private ObjectLinkedList<T> owner;
        private Node<T> previous;
        private Node<T> next;
        private Node<T> poolNext;
        private T value;

        private Node() {
        }

        /**
         * Returns the previous node.
         *
         * @return the previous node, or null
         */
        public Node<T> previous() {
            return previous;
        }

        /**
         * Returns the next node.
         *
         * @return the next node, or null
         */
        public Node<T> next() {
            return next;
        }

        /**
         * Returns the value.
         *
         * @return the value
         */
        public T value() {
            return value;
        }
    }

    private Node<T> obtain(T value) {
        if (free == null) {
            addFreeNodes(Math.max(8, capacity >> 1));
        }
        Node<T> node = free;
        free = node.poolNext;
        node.poolNext = null;
        node.owner = this;
        node.value = value;
        return node;
    }

    private void release(Node<T> node) {
        node.owner = null;
        node.previous = null;
        node.next = null;
        node.value = null;
        node.poolNext = free;
        free = node;
    }

    private void addFreeNodes(int count) {
        for (int i = 0; i < count; i++) {
            Node<T> node = new Node<T>();
            node.poolNext = free;
            free = node;
        }
        capacity += count;
    }

    private static final class ListIterator<T> implements ObjectIterator<T> {
        private final ObjectLinkedList<T> list;
        private Node<T> node;

        ListIterator(ObjectLinkedList<T> list) {
            this.list = list;
        }

        @Override
        public ObjectIterator<T> reset() {
            node = list.first;
            return this;
        }

        @Override
        public boolean hasNext() {
            return node != null;
        }

        @Override
        public T next() {
            if (node == null) {
                throw new NoSuchElementException();
            }
            T value = node.value;
            node = node.next;
            return value;
        }
    }
}
