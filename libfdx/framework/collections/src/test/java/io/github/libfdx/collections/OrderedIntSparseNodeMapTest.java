package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class OrderedIntSparseNodeMapTest {
    @Test
    void supportsKeyedDenseAndOrderedAccess() {
        OrderedIntSparseNodeMap<String, TestNode> map = map(4, 3);
        TestNode zero = map.putNode(0, "zero");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");
        two.metadata = 42;

        assertEquals(3, map.size());
        assertEquals("two", map.get(2));
        assertEquals("fallback", map.get(7, "fallback"));
        assertNull(map.get(-1));
        assertSame(two, map.getNode(2));
        assertTrue(map.containsKey(3));
        assertFalse(map.containsKey(-1));
        assertSame(zero, map.firstNode());
        assertSame(three, map.lastNode());
        assertSame(zero, map.nodeAt(0));
        assertSame(two, map.nodeAt(1));

        assertEquals("two", map.put(2, "TWO"));
        assertSame(two, map.putNode(2, "second"));
        assertEquals(42, two.metadata);
        assertEquals("second", two.value());
        assertOrdered(map, zero, two, three);
    }

    @Test
    void swapRemovalRepairsSparseAndDenseIndices() {
        OrderedIntSparseNodeMap<String, TestNode> map = map(8, 4);
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");
        TestNode seven = map.putNode(7, "seven");

        assertEquals("two", map.remove(2));

        assertFalse(two.isActive());
        assertNull(map.get(2));
        assertFalse(map.containsKey(2));
        assertSame(seven, map.getNode(7));
        assertSame(seven, map.nodeAt(1));
        assertEquals(1, seven.denseIndex());
        assertOrdered(map, one, three, seven);

        assertEquals("one", map.removeNode(one));
        assertSame(three, map.firstNode());
        assertOrdered(map, three, seven);
    }

    @Test
    void supportsLogicalReorderingWithoutChangingDenseStorage() {
        OrderedIntSparseNodeMap<String, TestNode> map = map(4, 3);
        TestNode zero = map.putNode(0, "zero");
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");

        map.moveToFirst(two);
        map.moveToLast(zero);

        assertOrdered(map, two, one, zero);
        assertSame(zero, map.nodeAt(0));
        assertSame(one, map.nodeAt(1));
        assertSame(two, map.nodeAt(2));
    }

    @Test
    void growsSparseAndNodeStorageAndReusesPooledNodes() {
        OrderedIntSparseNodeMap<String, TestNode> map = map(0, 1);
        TestNode first = map.putNode(0, "zero");
        first.metadata = 99;
        map.put(100, "hundred");
        int grownNodeCapacity = map.nodeCapacity();

        assertTrue(map.keyCapacity() >= 101);
        assertEquals("hundred", map.get(100));
        assertEquals("zero", map.removeNode(first));
        assertEquals(0, first.metadata);
        assertEquals(1, first.resetCount);
        assertSame(first, map.putNode(50, "reused"));

        map.clear();
        for (int i = 0; i < grownNodeCapacity; i++) {
            map.put(i, "value");
        }
        assertEquals(grownNodeCapacity, map.size());
        assertEquals(grownNodeCapacity, map.nodeCapacity());
        assertNull(map.get(100));
    }

    @Test
    void cachesOrderedAndDenseIterators() {
        OrderedIntSparseNodeMap<String, TestNode> map = map(4, 3);
        TestNode zero = map.putNode(0, "zero");
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        map.removeNode(one);

        ObjectIterator<TestNode> ordered = map.iterator();
        assertSame(ordered, map.iterator());
        assertSame(zero, ordered.next());
        assertSame(two, ordered.next());
        assertFalse(ordered.hasNext());
        assertThrows(NoSuchElementException.class, ordered::next);

        ArrayView<TestNode> dense = map.denseNodes();
        ObjectIterator<TestNode> denseIterator = dense.iterator();
        assertSame(dense, map.denseNodes());
        assertSame(denseIterator, dense.iterator());
        assertEquals(2, dense.size());
        assertSame(zero, dense.first());
        assertSame(two, dense.peek());
        assertTrue(dense.contains(two, true));
        assertEquals(1, dense.indexOf(two, true));
        assertEquals(1, dense.lastIndexOf(two, true));
        assertSame(zero, denseIterator.next());
        assertSame(two, denseIterator.next());
        assertFalse(denseIterator.hasNext());
        assertThrows(NoSuchElementException.class, denseIterator::next);

        TestNode[] copied = dense.toArray(new TestNode[0]);
        assertSame(zero, copied[0]);
        assertSame(two, copied[1]);
    }

    @Test
    void validatesKeysNodesCapacitiesAndFactories() {
        OrderedIntSparseNodeMap<String, TestNode> first = map(0, 0);
        OrderedIntSparseNodeMap<String, TestNode> second = map(1, 1);
        first.ensureKeyCapacity(8).ensureCapacity(3);
        TestNode node = first.putNode(7, "seven");

        assertTrue(first.keyCapacity() >= 8);
        assertEquals(3, first.nodeCapacity());
        assertThrows(IllegalArgumentException.class, () -> first.put(-1, "negative"));
        assertThrows(IllegalArgumentException.class,
                () -> first.put(Integer.MAX_VALUE, "too-large"));
        assertThrows(IllegalArgumentException.class, () -> first.ensureKeyCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> first.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> second.removeNode(node));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntSparseNodeMap<String, TestNode>(-1, 1, TestNode::new));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntSparseNodeMap<String, TestNode>(1, -1, TestNode::new));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntSparseNodeMap<String, TestNode>(null));
        assertThrows(IllegalStateException.class,
                () -> new OrderedIntSparseNodeMap<String, TestNode>(1, () -> null));
    }

    private static OrderedIntSparseNodeMap<String, TestNode> map(int keyCapacity,
            int nodeCapacity) {
        return new OrderedIntSparseNodeMap<String, TestNode>(
                keyCapacity, nodeCapacity, TestNode::new);
    }

    private static void assertOrdered(OrderedIntSparseNodeMap<String, TestNode> map,
            TestNode... expected) {
        ObjectIterator<TestNode> iterator = map.iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertSame(expected[i], iterator.next());
        }
        assertFalse(iterator.hasNext());
    }

    private static final class TestNode
            extends OrderedIntSparseNodeMap.Node<String, TestNode> {
        private int metadata;
        private int resetCount;

        @Override
        protected void reset() {
            metadata = 0;
            resetCount++;
        }
    }
}
