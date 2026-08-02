package io.github.libfdx.collections;

import java.util.NoSuchElementException;

/**
 * Maps primitive int keys to customizable pooled nodes with both stable ordered
 * traversal and dense unordered indexed access.
 *
 * <p>Key lookup is delegated to an {@link IntMap}. The same node instances are
 * stored in an unordered dense {@link Array}; removal swaps the last dense node
 * into the removed position and repairs its index. Node references form an
 * independent doubly linked insertion order, so dense storage changes do not
 * disturb ordered traversal.</p>
 *
 * <p>Lookup and removal are expected constant time, insertion is amortized
 * constant time, and either complete traversal is linear in {@link #size()}.
 * Ordered traversal follows node links, while dense traversal through
 * {@link #nodeAt(int)} or {@link #denseNodes()} visits packed array positions.</p>
 *
 * <p>Removed nodes are retained in a per-map pool. A node reference is valid
 * only while {@link Node#isActive()} is true and must not be retained after
 * removal because a later insertion may reuse that instance.</p>
 *
 * <pre>{@code
 * final class EntityNode extends OrderedIntNodeMap.Node<Entity, EntityNode> {
 *     int renderLayer;
 *
 *     @Override
 *     protected void reset() {
 *         renderLayer = 0;
 *     }
 * }
 *
 * OrderedIntNodeMap<Entity, EntityNode> entities =
 *         new OrderedIntNodeMap<Entity, EntityNode>(EntityNode::new);
 * EntityNode node = entities.putNode(entityId, entity);
 * node.renderLayer = 2;
 * }</pre>
 *
 * @param <V> the value type
 * @param <N> the customizable node type
 * @author xpenatan
 */
public final class OrderedIntNodeMap<V, N extends OrderedIntNodeMap.Node<V, N>>
        implements ObjectIterable<N> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private final IntMap<N> nodesByKey;
    private final Array<N> denseNodes;
    private final Array<N> freeNodes;
    private final NodeFactory<N> nodeFactory;
    private N first;
    private N last;
    private int nodeCapacity;
    private OrderedNodeIterator<V, N> iterator;

    /**
     * Creates a map.
     *
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntNodeMap(NodeFactory<N> nodeFactory) {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, nodeFactory);
    }

    /**
     * Creates a map with enough pooled nodes for the expected number of entries.
     *
     * @param capacity the expected entry capacity
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntNodeMap(int capacity, NodeFactory<N> nodeFactory) {
        this(capacity, DEFAULT_LOAD_FACTOR, nodeFactory);
    }

    /**
     * Creates a map with enough pooled nodes for the expected number of entries.
     *
     * @param capacity the expected entry capacity
     * @param loadFactor the internal int map load factor
     * @param nodeFactory creates new customizable nodes when the pool grows
     */
    public OrderedIntNodeMap(int capacity, float loadFactor, NodeFactory<N> nodeFactory) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (nodeFactory == null) {
            throw new IllegalArgumentException("nodeFactory must not be null");
        }
        this.nodeFactory = nodeFactory;
        nodesByKey = new IntMap<N>(capacity, loadFactor);
        denseNodes = new Array<N>(false, capacity);
        freeNodes = new Array<N>(false, capacity);
        addFreeNodes(capacity);
    }

    /**
     * Adds or replaces a value. Replacement keeps the existing node, its custom
     * state, and its logical order.
     *
     * @param key the key
     * @param value the value
     * @return the previous value, or null
     */
    public V put(int key, V value) {
        N existing = nodesByKey.get(key);
        if (existing != null) {
            V previous = existing.value;
            existing.value = value;
            return previous;
        }
        N node = obtain(key, value);
        nodesByKey.put(key, node);
        activate(node);
        return null;
    }

    /**
     * Adds or replaces a value and returns its active customizable node.
     * Replacement returns the existing node without clearing custom state.
     *
     * @param key the key
     * @param value the value
     * @return the active node
     */
    public N putNode(int key, V value) {
        N existing = nodesByKey.get(key);
        if (existing != null) {
            existing.value = value;
            return existing;
        }
        N node = obtain(key, value);
        nodesByKey.put(key, node);
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
        N node = nodesByKey.get(key);
        return node != null ? node.value : null;
    }

    /**
     * Returns a value or a default when its key is absent.
     *
     * @param key the key
     * @param defaultValue the value returned when the key is absent
     * @return the stored value or default value
     */
    public V get(int key, V defaultValue) {
        N node = nodesByKey.get(key);
        return node != null ? node.value : defaultValue;
    }

    /**
     * Returns the active node for a key.
     *
     * @param key the key
     * @return the active node, or null
     */
    public N getNode(int key) {
        return nodesByKey.get(key);
    }

    /**
     * Returns whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean containsKey(int key) {
        return nodesByKey.containsKey(key);
    }

    /**
     * Removes a key and pools its node.
     *
     * @param key the key
     * @return the previous value, or null
     */
    public V remove(int key) {
        N node = nodesByKey.remove(key);
        return node != null ? removeActiveNode(node) : null;
    }

    /**
     * Removes an active node and returns it to this map's pool.
     *
     * @param node the active node
     * @return the removed value
     */
    public V removeNode(N node) {
        requireActiveNode(node);
        N removed = nodesByKey.remove(node.key);
        if (removed != node) {
            throw new IllegalStateException("node index is inconsistent with the key map");
        }
        return removeActiveNode(node);
    }

    /**
     * Moves an active node to the beginning of logical ordered traversal.
     * Dense storage is unchanged.
     *
     * @param node the active node
     * @return this map
     */
    public OrderedIntNodeMap<V, N> moveToFirst(N node) {
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
     * Moves an active node to the end of logical ordered traversal.
     * Dense storage is unchanged.
     *
     * @param node the active node
     * @return this map
     */
    public OrderedIntNodeMap<V, N> moveToLast(N node) {
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

    /** Removes all entries and retains their nodes for reuse. */
    public void clear() {
        nodesByKey.clear();
        for (int i = 0; i < denseNodes.size(); i++) {
            release(denseNodes.get(i));
        }
        denseNodes.clear();
        first = null;
        last = null;
    }

    /**
     * Ensures that additional entries can be inserted without growing the key
     * map, dense array, or node pool.
     *
     * @param additionalCapacity the additional entries to reserve
     * @return this map
     */
    public OrderedIntNodeMap<V, N> ensureCapacity(int additionalCapacity) {
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("additionalCapacity must be >= 0");
        }
        nodesByKey.ensureCapacity(additionalCapacity);
        denseNodes.ensureCapacity(additionalCapacity);
        int required = size() + additionalCapacity;
        if (required > nodeCapacity) {
            addFreeNodes(required - nodeCapacity);
        }
        return this;
    }

    /**
     * Returns the number of active entries.
     *
     * @return the entry count
     */
    public int size() {
        return denseNodes.size();
    }

    /**
     * Returns whether the map is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return denseNodes.isEmpty();
    }

    /**
     * Returns whether the map contains at least one entry.
     *
     * @return true if not empty
     */
    public boolean notEmpty() {
        return denseNodes.notEmpty();
    }

    /**
     * Returns the number of active and pooled node instances retained by this map.
     *
     * @return the node capacity
     */
    public int nodeCapacity() {
        return nodeCapacity;
    }

    /**
     * Returns the current internal key table capacity.
     *
     * @return the key table capacity
     */
    public int tableCapacity() {
        return nodesByKey.capacity();
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
        return denseNodes.get(index);
    }

    /**
     * Returns a cached read-only live view of dense unordered nodes.
     *
     * @return dense nodes
     */
    public ArrayView<N> denseNodes() {
        return denseNodes.view();
    }

    /**
     * Returns this map's ordered node iterable.
     *
     * @return nodes in logical order
     */
    public ObjectIterable<N> orderedNodes() {
        return this;
    }

    /**
     * Returns a cached iterator that follows logical node order.
     *
     * @return the reset ordered iterator
     */
    @Override
    public ObjectIterator<N> iterator() {
        if (iterator == null) {
            iterator = new OrderedNodeIterator<V, N>(this);
        }
        return iterator.reset();
    }

    private void activate(N node) {
        denseNodes.add(node);
        linkLast(node);
    }

    private V removeActiveNode(N node) {
        V value = node.value;
        int denseIndex = node.denseIndex;
        unlink(node);
        denseNodes.removeIndex(denseIndex);
        if (denseIndex < denseNodes.size()) {
            denseNodes.get(denseIndex).denseIndex = denseIndex;
        }
        releaseUnlinked(node);
        return value;
    }

    private N obtain(int key, V value) {
        if (freeNodes.isEmpty()) {
            addFreeNodes(Math.max(8, nodeCapacity >> 1));
        }
        N node = freeNodes.pop();
        node.key = key;
        node.denseIndex = denseNodes.size();
        node.value = value;
        return node;
    }

    private void release(N node) {
        node.previous = null;
        node.next = null;
        releaseUnlinked(node);
    }

    private void releaseUnlinked(N node) {
        node.key = 0;
        node.denseIndex = -1;
        node.value = null;
        node.reset();
        freeNodes.add(node);
    }

    private void addFreeNodes(int count) {
        freeNodes.ensureCapacity(denseNodes.size() + count);
        for (int i = 0; i < count; i++) {
            N node = nodeFactory.create();
            if (node == null) {
                throw new IllegalStateException("nodeFactory returned null");
            }
            if (node.owner != null) {
                throw new IllegalStateException("nodeFactory returned a node that is already owned");
            }
            node.owner = this;
            node.denseIndex = -1;
            freeNodes.add(node);
            nodeCapacity++;
        }
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

    /** Creates user-defined node instances for this map's internal pool. */
    @FunctionalInterface
    public interface NodeFactory<N> {
        /**
         * Creates one new node instance.
         *
         * @return a distinct unowned node
         */
        N create();
    }

    /**
     * Base class for customizable map nodes.
     *
     * <p>Subclasses may add arbitrary fields. Override {@link #reset()} to
     * clear that custom state when a node is returned to the pool. Structural
     * key, index, and link fields are managed exclusively by the owning map.</p>
     *
     * @param <V> the value type
     * @param <N> the concrete self type
     */
    public abstract static class Node<V, N extends Node<V, N>> {
        OrderedIntNodeMap<?, ?> owner;
        N previous;
        N next;
        int key;
        int denseIndex = -1;
        V value;

        /** Creates an unowned node. */
        protected Node() {
        }

        /**
         * Returns whether this node currently represents a map entry.
         *
         * @return true while active
         */
        public final boolean isActive() {
            return denseIndex >= 0;
        }

        /**
         * Returns the primitive map key.
         *
         * @return the active key
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

        /** Clears subclass-specific state after this node becomes inactive. */
        protected void reset() {
        }
    }

    private static final class OrderedNodeIterator<V, N extends Node<V, N>>
            implements ObjectIterator<N> {
        private final OrderedIntNodeMap<V, N> map;
        private N next;

        OrderedNodeIterator(OrderedIntNodeMap<V, N> map) {
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
