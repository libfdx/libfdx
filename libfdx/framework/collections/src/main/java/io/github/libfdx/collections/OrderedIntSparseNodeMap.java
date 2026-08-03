package io.github.libfdx.collections;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Maps non-negative int keys to customizable pooled nodes using sparse-set
 * lookup, dense unordered indexed storage, and stable ordered traversal.
 *
 * <p><b>Algorithm:</b> A sparse array maps each key directly to the same node
 * stored in a partitioned node-slot array. Active nodes occupy a packed prefix
 * and pooled nodes occupy the remaining slots. Removal clears the direct key
 * slot, swaps the last active node into the removed position, and leaves the
 * removed node in the first pooled slot. Independent node links maintain a
 * separate logical sequence.</p>
 *
 * <p><b>Ordering:</b> Ordered traversal through {@link #iterator()} or
 * {@link #orderedNodes()} follows insertion order, as modified by
 * {@link #moveToFirst(Node)} and {@link #moveToLast(Node)}. Dense traversal
 * through {@link #nodeAt(int)} or {@link #denseNodes()} is unordered and may
 * change after removal.</p>
 *
 * <p><b>Performance:</b> With sufficient reserved capacity, put, get, key
 * lookup, key removal, node removal, dense indexed access, first/last access,
 * and order moves are worst-case {@code O(1)}. A put that grows sparse-key or
 * node storage is {@code O(new capacity)}; geometric growth makes gradual
 * expansion amortized {@code O(1)}. Either complete traversal is
 * {@code O(n)}.</p>
 *
 * <p><b>Key range:</b> This design is intended for compact non-negative IDs such as entity or
 * component IDs. Sparse storage is proportional to the largest addressable key,
 * so {@link OrderedIntNodeMap} is more appropriate for negative or widely
 * separated keys.</p>
 *
 * <p><b>Pooling:</b> Removed nodes are retained in a per-map pool. A node reference is valid
 * only while {@link Node#isActive()} is true and must not be retained after
 * removal because a later insertion may reuse that instance.</p>
 *
 * <pre>{@code
 * final class EntityNode extends OrderedIntSparseNodeMap.Node<Entity, EntityNode> {
 *     int renderLayer;
 *
 *     @Override
 *     protected void reset() {
 *         renderLayer = 0;
 *     }
 * }
 *
 * OrderedIntSparseNodeMap<Entity, EntityNode> entities =
 *         new OrderedIntSparseNodeMap<Entity, EntityNode>(4096, EntityNode::new);
 * EntityNode node = entities.putNode(entityId, entity);
 * node.renderLayer = 2;
 * }</pre>
 *
 * @param <V> the value type
 * @param <N> the customizable node type
 * @author xpenatan
 */
public final class OrderedIntSparseNodeMap<V,
        N extends OrderedIntSparseNodeMap.Node<V, N>> implements ObjectIterable<N> {
    private static final int DEFAULT_CAPACITY = 16;

    private Object[] sparseNodes;
    private Object[] nodeSlots;
    private final NodeFactory<N> nodeFactory;
    private N first;
    private N last;
    private int size;
    private DenseNodeView<V, N> denseNodeView;
    private OrderedNodeIterator<V, N> iterator;

    /**
     * Creates a sparse node map with default key and node capacity.
     *
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntSparseNodeMap(NodeFactory<N> nodeFactory) {
        this(DEFAULT_CAPACITY, DEFAULT_CAPACITY, nodeFactory);
    }

    /**
     * Creates a map with the same initial key and node capacity.
     *
     * @param capacity initial exclusive key limit and expected entry capacity
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntSparseNodeMap(int capacity, NodeFactory<N> nodeFactory) {
        this(capacity, capacity, nodeFactory);
    }

    /**
     * Creates a map with independent initial key and node capacities.
     *
     * @param keyCapacity initial exclusive key limit
     * @param nodeCapacity expected entry capacity
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntSparseNodeMap(int keyCapacity, int nodeCapacity,
            NodeFactory<N> nodeFactory) {
        if (keyCapacity < 0) {
            throw new IllegalArgumentException("keyCapacity must be >= 0");
        }
        if (nodeCapacity < 0) {
            throw new IllegalArgumentException("nodeCapacity must be >= 0");
        }
        if (nodeFactory == null) {
            throw new IllegalArgumentException("nodeFactory must not be null");
        }
        this.nodeFactory = nodeFactory;
        sparseNodes = new Object[keyCapacity];
        nodeSlots = new Object[0];
        addNodeSlots(nodeCapacity);
    }

    /**
     * Adds or replaces a value. Replacement retains the node, custom state,
     * and logical order.
     *
     * @param key the non-negative key
     * @param value the value
     * @return the previous value, or null
     */
    public V put(int key, V value) {
        ensureKeyAddress(key);
        N existing = findNode(key);
        if (existing != null) {
            V previous = existing.value;
            existing.value = value;
            return previous;
        }
        activate(obtain(key, value));
        return null;
    }

    /**
     * Adds or replaces a value and returns its active customizable node.
     * Replacement retains custom state and logical order.
     *
     * @param key the non-negative key
     * @param value the value
     * @return the active node
     */
    public N putNode(int key, V value) {
        ensureKeyAddress(key);
        N existing = findNode(key);
        if (existing != null) {
            existing.value = value;
            return existing;
        }
        N node = obtain(key, value);
        activate(node);
        return node;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @return the value, or null
     */
    public V get(int key) {
        N node = findNode(key);
        return node != null ? node.value : null;
    }

    /**
     * Returns a value or a default when the key is absent.
     *
     * @param key the key
     * @param defaultValue the value returned when absent
     * @return the stored value or default value
     */
    public V get(int key, V defaultValue) {
        N node = findNode(key);
        return node != null ? node.value : defaultValue;
    }

    /**
     * Returns the active node for a key.
     *
     * @param key the key
     * @return the node, or null
     */
    public N getNode(int key) {
        return findNode(key);
    }

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean containsKey(int key) {
        return findNode(key) != null;
    }

    /**
     * Removes a key and returns its node to the pool.
     *
     * @param key the key
     * @return the previous value, or null
     */
    @SuppressWarnings("unchecked")
    public V remove(int key) {
        if (key < 0 || key >= sparseNodes.length) {
            return null;
        }
        N node = (N)sparseNodes[key];
        return node != null ? removeActiveNode(node) : null;
    }

    /**
     * Removes an active node without performing a key lookup.
     *
     * @param node the active node
     * @return the removed value
     */
    public V removeNode(N node) {
        requireActiveNode(node);
        return removeActiveNode(node);
    }

    /**
     * Moves an active node to the beginning of logical traversal.
     * Dense storage is unchanged.
     *
     * @param node the active node
     * @return this map
     */
    public OrderedIntSparseNodeMap<V, N> moveToFirst(N node) {
        requireActiveNode(node);
        if (node == first) {
            return this;
        }
        unlink(node);
        node.previous = null;
        node.next = first;
        first.previous = node;
        first = node;
        return this;
    }

    /**
     * Moves an active node to the end of logical traversal.
     * Dense storage is unchanged.
     *
     * @param node the active node
     * @return this map
     */
    public OrderedIntSparseNodeMap<V, N> moveToLast(N node) {
        requireActiveNode(node);
        if (node == last) {
            return this;
        }
        unlink(node);
        node.previous = last;
        node.next = null;
        last.next = node;
        last = node;
        return this;
    }

    /** Removes all entries while retaining sparse and pooled storage. */
    public void clear() {
        for (int i = 0; i < size; i++) {
            N node = nodeAtUnchecked(i);
            sparseNodes[node.key] = null;
            release(node);
        }
        size = 0;
        first = null;
        last = null;
    }

    /**
     * Ensures that additional nodes can be inserted without growing the
     * shared active-and-pooled node storage.
     *
     * @param additionalCapacity additional entry capacity
     * @return this map
     */
    public OrderedIntSparseNodeMap<V, N> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        int required = size() + additionalCapacity;
        if (required > nodeSlots.length) {
            addNodeSlots(required - nodeSlots.length);
        }
        return this;
    }

    /**
     * Ensures that every key below the exclusive capacity can be addressed
     * without growing sparse storage.
     *
     * @param keyCapacity the exclusive key capacity
     * @return this map
     */
    public OrderedIntSparseNodeMap<V, N> ensureKeyCapacity(int keyCapacity) {
        if (keyCapacity < 0) {
            throw new IllegalArgumentException("keyCapacity must be >= 0");
        }
        if (keyCapacity > sparseNodes.length) {
            sparseNodes = Arrays.copyOf(sparseNodes, keyCapacity);
        }
        return this;
    }

    /**
     * Returns the active entry count.
     *
     * @return the entry count
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this map is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this map contains entries.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return size != 0;
    }

    /**
     * Returns the current exclusive key capacity.
     *
     * @return the sparse key capacity
     */
    public int keyCapacity() {
        return sparseNodes.length;
    }

    /**
     * Returns the number of active and pooled nodes retained by this map.
     *
     * @return the node capacity
     */
    public int nodeCapacity() {
        return nodeSlots.length;
    }

    /**
     * Returns the first node in logical order.
     *
     * @return the first node, or null
     */
    public N firstNode() {
        return first;
    }

    /**
     * Returns the last node in logical order.
     *
     * @return the last node, or null
     */
    public N lastNode() {
        return last;
    }

    /**
     * Returns a node by its current dense unordered index.
     *
     * @param index the dense index
     * @return the active node
     */
    public N nodeAt(int index) {
        checkDenseIndex(index);
        return nodeAtUnchecked(index);
    }

    /**
     * Returns a cached read-only live view of dense unordered nodes.
     *
     * @return dense nodes
     */
    public ArrayView<N> denseNodes() {
        if (denseNodeView == null) {
            denseNodeView = new DenseNodeView<V, N>(this);
        }
        return denseNodeView;
    }

    /**
     * Returns this map's logical ordered node iterable.
     *
     * @return ordered nodes
     */
    public ObjectIterable<N> orderedNodes() {
        return this;
    }

    /**
     * Returns the cached reset iterator following logical node order.
     *
     * @return the ordered iterator
     */
    @Override
    public ObjectIterator<N> iterator() {
        if (iterator == null) {
            iterator = new OrderedNodeIterator<V, N>(this);
        }
        return iterator.reset();
    }

    @SuppressWarnings("unchecked")
    private N findNode(int key) {
        if (key < 0 || key >= sparseNodes.length) {
            return null;
        }
        return (N)sparseNodes[key];
    }

    private void activate(N node) {
        size++;
        sparseNodes[node.key] = node;
        linkLast(node);
    }

    private V removeActiveNode(N node) {
        V value = node.value;
        sparseNodes[node.key] = null;
        unlink(node);
        int denseIndex = node.denseIndex;
        int lastIndex = --size;
        if (denseIndex != lastIndex) {
            N moved = nodeAtUnchecked(lastIndex);
            nodeSlots[denseIndex] = moved;
            moved.denseIndex = denseIndex;
            nodeSlots[lastIndex] = node;
        }
        releaseUnlinked(node);
        return value;
    }

    private N obtain(int key, V value) {
        if (size == nodeSlots.length) {
            addNodeSlots(Math.max(8, nodeSlots.length >> 1));
        }
        N node = nodeAtUnchecked(size);
        node.key = key;
        node.denseIndex = size;
        node.value = value;
        return node;
    }

    private void release(N node) {
        node.previous = null;
        node.next = null;
        releaseUnlinked(node);
    }

    private void releaseUnlinked(N node) {
        node.denseIndex = -1;
        node.value = null;
        node.reset();
    }

    private void addNodeSlots(int count) {
        int oldCapacity = nodeSlots.length;
        int newCapacity = oldCapacity + count;
        nodeSlots = Arrays.copyOf(nodeSlots, newCapacity);
        for (int i = oldCapacity; i < newCapacity; i++) {
            N node = nodeFactory.create();
            if (node == null) {
                throw new IllegalStateException("nodeFactory returned null");
            }
            if (node.owner != null) {
                throw new IllegalStateException("nodeFactory returned a node that is already owned");
            }
            node.owner = this;
            node.denseIndex = -1;
            nodeSlots[i] = node;
        }
    }

    @SuppressWarnings("unchecked")
    private N nodeAtUnchecked(int index) {
        return (N)nodeSlots[index];
    }

    private void checkDenseIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void ensureKeyAddress(int key) {
        if (key < 0) {
            throw new IllegalArgumentException("key must be >= 0");
        }
        if (key < sparseNodes.length) {
            return;
        }
        if (key == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("key is too large");
        }
        int required = key + 1;
        int grown = sparseNodes.length + (sparseNodes.length >> 1) + 1;
        ensureKeyCapacity(Math.max(required, grown));
    }

    private void linkLast(N node) {
        node.previous = last;
        node.next = null;
        if (last != null) {
            last.next = node;
        }
        else {
            first = node;
        }
        last = node;
    }

    private void unlink(N node) {
        N previous = node.previous;
        N next = node.next;
        if (previous != null) {
            previous.next = next;
        }
        else {
            first = next;
        }
        if (next != null) {
            next.previous = previous;
        }
        else {
            last = previous;
        }
        node.previous = null;
        node.next = null;
    }

    private void requireActiveNode(N node) {
        if (node == null || node.owner != this || node.denseIndex < 0) {
            throw new IllegalArgumentException("node does not belong to this map");
        }
    }

    /** Creates user-defined nodes for this map's internal pool. */
    @FunctionalInterface
    public interface NodeFactory<N> {
        /**
         * Creates one new node.
         *
         * @return a distinct unowned node
         */
        N create();
    }

    /**
     * Base class for customizable sparse-map nodes.
     *
     * <p>Subclasses may add fields and override {@link #reset()} to clear their
     * state when returned to the pool. Structural fields are owned by the map.</p>
     *
     * @param <V> the value type
     * @param <N> the concrete self type
     */
    public abstract static class Node<V, N extends Node<V, N>> {
        OrderedIntSparseNodeMap<?, ?> owner;
        N previous;
        N next;
        int key;
        int denseIndex = -1;
        V value;

        /** Creates an unowned node. */
        protected Node() {
        }

        /**
         * Returns whether this node currently represents an entry.
         *
         * @return true while active
         */
        public final boolean isActive() {
            return denseIndex >= 0;
        }

        /**
         * Returns the active primitive key.
         *
         * @return the key
         */
        public final int key() {
            return key;
        }

        /**
         * Returns the current dense unordered index.
         *
         * @return the dense index, or -1 while pooled
         */
        public final int denseIndex() {
            return denseIndex;
        }

        /**
         * Returns the mapped value.
         *
         * @return the value, or null while pooled
         */
        public final V value() {
            return value;
        }

        /**
         * Returns the previous node in logical order.
         *
         * @return the previous node, or null
         */
        public final N previous() {
            return previous;
        }

        /**
         * Returns the next node in logical order.
         *
         * @return the next node, or null
         */
        public final N next() {
            return next;
        }

        /** Clears subclass state after this node becomes inactive. */
        protected void reset() {
        }
    }

    private static final class DenseNodeView<V, N extends Node<V, N>>
            implements ArrayView<N> {
        private final OrderedIntSparseNodeMap<V, N> map;
        private DenseNodeIterator<V, N> iterator;

        DenseNodeView(OrderedIntSparseNodeMap<V, N> map) {
            this.map = map;
        }

        @Override
        public N get(int index) {
            return map.nodeAt(index);
        }

        @Override
        public N first() {
            if (map.size == 0) {
                throw new NoSuchElementException("OrderedIntSparseNodeMap is empty");
            }
            return map.nodeAtUnchecked(0);
        }

        @Override
        public N peek() {
            if (map.size == 0) {
                throw new NoSuchElementException("OrderedIntSparseNodeMap is empty");
            }
            return map.nodeAtUnchecked(map.size - 1);
        }

        @Override
        public boolean contains(N value) {
            return indexOf(value, false) >= 0;
        }

        @Override
        public boolean contains(N value, boolean identity) {
            return indexOf(value, identity) >= 0;
        }

        @Override
        public int indexOf(N value) {
            return indexOf(value, false);
        }

        @Override
        public int indexOf(N value, boolean identity) {
            for (int i = 0; i < map.size; i++) {
                if (valuesEqual(value, map.nodeAtUnchecked(i), identity)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(N value) {
            return lastIndexOf(value, false);
        }

        @Override
        public int lastIndexOf(N value, boolean identity) {
            for (int i = map.size - 1; i >= 0; i--) {
                if (valuesEqual(value, map.nodeAtUnchecked(i), identity)) {
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
            return Arrays.copyOf(map.nodeSlots, map.size);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A> A[] toArray(A[] destination) {
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (destination.length < map.size) {
                return (A[])Arrays.copyOf(map.nodeSlots, map.size, destination.getClass());
            }
            System.arraycopy(map.nodeSlots, 0, destination, 0, map.size);
            if (destination.length > map.size) {
                destination[map.size] = null;
            }
            return destination;
        }

        @Override
        public ObjectIterator<N> iterator() {
            if (iterator == null) {
                iterator = new DenseNodeIterator<V, N>(map);
            }
            return iterator.reset();
        }

        private static boolean valuesEqual(Object value, Object other, boolean identity) {
            if (identity) {
                return value == other;
            }
            return value == null ? other == null : value.equals(other);
        }
    }

    private static final class DenseNodeIterator<V, N extends Node<V, N>>
            implements ObjectIterator<N> {
        private final OrderedIntSparseNodeMap<V, N> map;
        private int index;

        DenseNodeIterator(OrderedIntSparseNodeMap<V, N> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<N> reset() {
            index = 0;
            return this;
        }

        @Override
        public boolean hasNext() {
            return index < map.size;
        }

        @Override
        public N next() {
            if (index >= map.size) {
                throw new NoSuchElementException();
            }
            return map.nodeAtUnchecked(index++);
        }
    }

    private static final class OrderedNodeIterator<V, N extends Node<V, N>>
            implements ObjectIterator<N> {
        private final OrderedIntSparseNodeMap<V, N> map;
        private N next;

        OrderedNodeIterator(OrderedIntSparseNodeMap<V, N> map) {
            this.map = map;
        }

        @Override
        public ObjectIterator<N> reset() {
            next = map.first;
            return this;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public N next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            N node = next;
            next = node.next;
            return node;
        }
    }
}
