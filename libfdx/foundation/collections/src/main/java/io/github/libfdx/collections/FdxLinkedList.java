package io.github.libfdx.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stores values in a doubly linked list.
 *
 * @param <T> the value type
 * @author xpenatan
 */
public final class FdxLinkedList<T> implements Iterable<T> {
    private Node<T> first;
    private Node<T> last;
    private int size;

    /**
     * Creates a linked list.
     */
    public FdxLinkedList() {
    }

    /**
     * Adds a value at the front.
     *
     * @param value the value
     * @return the created node
     */
    public Node<T> addFirst(T value) {
        Node<T> node = new Node<T>(value);
        node.owner = this;
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
     * @param value the value
     * @return the created node
     */
    public Node<T> addLast(T value) {
        Node<T> node = new Node<T>(value);
        node.owner = this;
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
     * @param node the node
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
        node.owner = null;
        node.previous = null;
        node.next = null;
        size--;
        return node.value;
    }

    /**
     * Removes all values.
     */
    public void clear() {
        Node<T> node = first;
        while (node != null) {
            Node<T> next = node.next;
            node.owner = null;
            node.previous = null;
            node.next = null;
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

    @Override
    public Iterator<T> iterator() {
        return new ListIterator<T>(first);
    }

    /**
     * Represents a linked list node.
     *
     * @param <T> the value type
     * @author xpenatan
     */
    public static final class Node<T> {
        private FdxLinkedList<T> owner;
        private Node<T> previous;
        private Node<T> next;
        private final T value;

        private Node(T value) {
            this.value = value;
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

    private static final class ListIterator<T> implements Iterator<T> {
        private Node<T> node;

        ListIterator(Node<T> node) {
            this.node = node;
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
