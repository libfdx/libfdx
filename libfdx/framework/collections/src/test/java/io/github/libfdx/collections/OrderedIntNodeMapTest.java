package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class OrderedIntNodeMapTest {
    @Test
    void supportsKeyedDenseAndOrderedAccess() {
        OrderedIntNodeMap<String, TestNode> map = map(3);
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");
        two.metadata = 42;

        assertEquals(3, map.size());
        assertEquals("two", map.get(2));
        assertEquals("fallback", map.get(9, "fallback"));
        assertSame(two, map.getNode(2));
        assertTrue(map.containsKey(3));
        assertTrue(map.notEmpty());
        assertFalse(map.isEmpty());
        assertSame(one, map.firstNode());
        assertSame(three, map.lastNode());
        assertSame(one, map.nodeAt(0));
        assertSame(two, map.nodeAt(1));
        assertSame(three, map.nodeAt(2));

        assertEquals("two", map.put(2, "TWO"));
        assertSame(two, map.putNode(2, "second"));
        assertEquals(42, two.metadata);
        assertEquals("second", two.value());
        assertSame(one, two.previous());
        assertSame(three, two.next());

        assertOrdered(map, one, two, three);
    }

    @Test
    void swapRemovalRepairsDenseIndexWithoutChangingLogicalOrder() {
        OrderedIntNodeMap<String, TestNode> map = map(4);
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");
        TestNode four = map.putNode(4, "four");

        assertEquals("two", map.remove(2));

        assertFalse(two.isActive());
        assertEquals(-1, two.denseIndex());
        assertNull(two.value());
        assertNull(two.previous());
        assertNull(two.next());
        assertSame(four, map.nodeAt(1));
        assertEquals(1, four.denseIndex());
        assertOrdered(map, one, three, four);

        assertEquals("one", map.removeNode(one));
        assertSame(three, map.firstNode());
        assertOrdered(map, three, four);
        assertEquals(2, map.size());
    }

    @Test
    void supportsLogicalReorderingWithoutChangingDenseStorage() {
        OrderedIntNodeMap<String, TestNode> map = map(3);
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");

        map.moveToFirst(three);
        assertOrdered(map, three, one, two);
        assertSame(one, map.nodeAt(0));
        assertSame(two, map.nodeAt(1));
        assertSame(three, map.nodeAt(2));

        map.moveToLast(one);
        assertOrdered(map, three, two, one);
        map.moveToFirst(three);
        map.moveToLast(one);
        assertOrdered(map, three, two, one);
    }

    @Test
    void poolsNodesAndResetsCustomState() {
        OrderedIntNodeMap<String, TestNode> map = map(2);
        TestNode node = map.putNode(7, "seven");
        node.metadata = 99;

        assertEquals("seven", map.put(7, "SEVEN"));
        assertSame(node, map.getNode(7));
        assertEquals(99, node.metadata);
        assertEquals(0, node.resetCount);

        assertEquals("SEVEN", map.remove(7));
        assertEquals(1, node.resetCount);
        assertEquals(0, node.metadata);

        TestNode reused = map.putNode(8, "eight");

        assertSame(node, reused);
        assertEquals(2, map.nodeCapacity());
        assertEquals(8, reused.key());
        assertEquals(0, reused.denseIndex());
        assertTrue(reused.isActive());

        map.clear();
        assertFalse(reused.isActive());
        assertEquals(2, reused.resetCount);
        assertTrue(map.isEmpty());
        assertNull(map.firstNode());
        assertNull(map.lastNode());
    }

    @Test
    void growingPoolWithActiveNodesRetainsEveryNodeForReuse() {
        OrderedIntNodeMap<String, TestNode> map = map(1);
        map.put(1, "one");
        map.put(2, "two");
        int grownCapacity = map.nodeCapacity();

        map.clear();
        for (int i = 0; i < grownCapacity; i++) {
            map.put(i, "value");
        }

        assertEquals(grownCapacity, map.size());
        assertEquals(grownCapacity, map.nodeCapacity());
    }

    @Test
    void cachesOrderedAndDenseIterators() {
        OrderedIntNodeMap<String, TestNode> map = map(3);
        TestNode one = map.putNode(1, "one");
        TestNode two = map.putNode(2, "two");
        TestNode three = map.putNode(3, "three");
        map.removeNode(two);

        ObjectIterator<TestNode> ordered = map.iterator();
        assertSame(ordered, map.iterator());
        assertSame(one, ordered.next());
        assertSame(three, ordered.next());
        assertFalse(ordered.hasNext());
        assertThrows(NoSuchElementException.class, ordered::next);

        ArrayView<TestNode> dense = map.denseNodes();
        ObjectIterator<TestNode> denseIterator = dense.iterator();
        assertSame(denseIterator, dense.iterator());
        assertEquals(2, dense.size());
        assertSame(one, denseIterator.next());
        assertSame(three, denseIterator.next());
        assertFalse(denseIterator.hasNext());
    }

    @Test
    void rejectsForeignInactiveAndInvalidNodes() {
        OrderedIntNodeMap<String, TestNode> first = map(1);
        OrderedIntNodeMap<String, TestNode> second = map(1);
        TestNode node = first.putNode(1, "one");

        assertThrows(IllegalArgumentException.class, () -> second.removeNode(node));
        assertThrows(IllegalArgumentException.class, () -> second.moveToFirst(node));
        first.removeNode(node);
        assertThrows(IllegalArgumentException.class, () -> first.removeNode(node));
        assertThrows(IllegalArgumentException.class, () -> first.moveToLast(node));
        assertThrows(IllegalArgumentException.class, () -> first.removeNode(null));
    }

    @Test
    void reservesNodesAndValidatesConstruction() {
        OrderedIntNodeMap<String, TestNode> map = map(0);

        map.ensureCapacity(3);

        assertEquals(3, map.nodeCapacity());
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");
        assertEquals(3, map.nodeCapacity());
        assertTrue(map.tableCapacity() >= 3);
        assertThrows(IllegalArgumentException.class, () -> map.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntNodeMap<String, TestNode>(-1, TestNode::new));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntNodeMap<String, TestNode>(null));
        assertThrows(IllegalStateException.class,
                () -> new OrderedIntNodeMap<String, TestNode>(1, () -> null));
    }

    private static OrderedIntNodeMap<String, TestNode> map(int capacity) {
        return new OrderedIntNodeMap<String, TestNode>(capacity, TestNode::new);
    }

    private static void assertOrdered(OrderedIntNodeMap<String, TestNode> map,
            TestNode... expected) {
        ObjectIterator<TestNode> iterator = map.iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertSame(expected[i], iterator.next());
        }
        assertFalse(iterator.hasNext());
    }

    private static final class TestNode extends OrderedIntNodeMap.Node<String, TestNode> {
        private int metadata;
        private int resetCount;

        @Override
        protected void reset() {
            metadata = 0;
            resetCount++;
        }
    }
}
