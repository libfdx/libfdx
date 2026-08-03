package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class OrderedIntSparseMapTest {
    @Test
    void supportsKeyedDenseAndOrderedAccess() {
        OrderedIntSparseMap<String> map = new OrderedIntSparseMap<String>(4, 4);
        map.put(0, "zero");
        map.put(2, "two");
        map.put(3, "three");
        map.put(1, null);

        assertEquals(4, map.size());
        assertEquals("two", map.get(2));
        assertEquals("fallback", map.get(7, "fallback"));
        assertNull(map.get(-1));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(null));
        assertFalse(map.containsKey(-1));
        assertEquals(0, map.firstKey());
        assertEquals(1, map.lastKey());
        assertEquals(0, map.keyAt(0));
        assertEquals("two", map.valueAt(1));

        assertEquals("two", map.put(2, "TWO"));
        assertEquals("TWO", map.get(2));
        assertOrderedKeys(map, 0, 2, 3, 1);
        assertOrderedValues(map, "zero", "TWO", "three", null);
    }

    @Test
    void swapRemovalRepairsSparseLookupAndPreservesLogicalOrder() {
        OrderedIntSparseMap<String> map = new OrderedIntSparseMap<String>(8, 4);
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");
        map.put(7, "seven");

        assertEquals("two", map.remove(2));

        assertFalse(map.containsKey(2));
        assertNull(map.get(2));
        assertEquals("seven", map.get(7));
        assertEquals(7, map.keyAt(1));
        assertEquals("seven", map.valueAt(1));
        assertOrderedKeys(map, 1, 3, 7);

        assertEquals("one", map.remove(1));
        assertEquals(3, map.firstKey());
        assertOrderedKeys(map, 3, 7);
        assertNull(map.remove(99));
    }

    @Test
    void reordersPrimitiveLinksWithoutChangingDenseStorage() {
        OrderedIntSparseMap<String> map = new OrderedIntSparseMap<String>(3);
        map.put(0, "zero");
        map.put(1, "one");
        map.put(2, "two");

        map.moveToFirst(2).moveToLast(0);

        assertOrderedKeys(map, 2, 1, 0);
        assertEquals(0, map.keyAt(0));
        assertEquals(1, map.keyAt(1));
        assertEquals(2, map.keyAt(2));
        assertEquals(1, map.findKey("one", -1));
        assertThrows(IllegalArgumentException.class, () -> map.moveToFirst(9));
        assertThrows(IllegalArgumentException.class, () -> map.moveToLast(-1));
    }

    @Test
    void growsClearsShrinksAndCopiesWithoutPerEntryNodes() {
        OrderedIntSparseMap<String> source = new OrderedIntSparseMap<String>(0, 0);
        source.ensureKeyCapacity(8).ensureCapacity(3);
        source.put(7, "seven");
        source.put(100, "hundred");

        assertTrue(source.keyCapacity() >= 101);
        assertEquals(3, source.capacity());

        OrderedIntSparseMap<String> copy = new OrderedIntSparseMap<String>(0, 0);
        copy.putAll(source);
        assertOrderedKeys(copy, 7, 100);
        assertEquals("hundred", copy.get(100));

        int keyCapacity = source.keyCapacity();
        int capacity = source.capacity();
        source.clear();
        assertTrue(source.isEmpty());
        assertEquals(-1, source.firstKey());
        assertEquals(-1, source.lastKey());
        assertEquals(keyCapacity, source.keyCapacity());
        assertEquals(capacity, source.capacity());

        source.shrink();
        assertEquals(0, source.keyCapacity());
        assertEquals(0, source.capacity());
        source.put(4, "four");
        assertEquals("four", source.get(4));
    }

    @Test
    void cachesOrderedAndDenseViewsIteratorsAndEntryObject() {
        OrderedIntSparseMap<String> map = new OrderedIntSparseMap<String>(4, 3);
        map.put(0, "zero");
        map.put(1, "one");
        map.put(2, "two");
        map.remove(1);

        ObjectIterable<OrderedIntSparseMap.Entry<String>> entries = map.entries();
        ObjectIterator<OrderedIntSparseMap.Entry<String>> entryIterator = entries.iterator();
        assertSame(entries, map.entries());
        assertSame(entryIterator, entries.iterator());
        OrderedIntSparseMap.Entry<String> entry = entryIterator.next();
        assertEquals(0, entry.key());
        assertEquals("zero", entry.value());
        assertSame(entry, entryIterator.next());
        assertEquals(2, entry.key());
        assertFalse(entryIterator.hasNext());
        assertThrows(NoSuchElementException.class, entryIterator::next);

        IntIterable keys = map.keys();
        IntIterator keyIterator = keys.iterator();
        assertSame(keys, map.keys());
        assertSame(keyIterator, keys.iterator());
        assertEquals(0, keyIterator.nextInt());
        assertEquals(2, keyIterator.nextInt());
        assertFalse(keyIterator.hasNext());

        ObjectIterable<String> values = map.values();
        ObjectIterator<String> valueIterator = values.iterator();
        assertSame(values, map.values());
        assertSame(valueIterator, values.iterator());
        assertEquals("zero", valueIterator.next());
        assertEquals("two", valueIterator.next());

        IntIterable denseKeys = map.denseKeys();
        IntIterator denseKeyIterator = denseKeys.iterator();
        assertSame(denseKeys, map.denseKeys());
        assertSame(denseKeyIterator, denseKeys.iterator());
        assertEquals(0, denseKeyIterator.nextInt());
        assertEquals(2, denseKeyIterator.nextInt());

        ArrayView<String> denseValues = map.denseValues();
        ObjectIterator<String> denseValueIterator = denseValues.iterator();
        assertSame(denseValues, map.denseValues());
        assertSame(denseValueIterator, denseValues.iterator());
        assertEquals(2, denseValues.size());
        assertEquals("zero", denseValues.first());
        assertEquals("two", denseValues.peek());
        assertTrue(denseValues.contains("two"));
        assertEquals(1, denseValues.indexOf("two"));
        assertEquals(1, denseValues.lastIndexOf("two"));
        assertEquals("zero", denseValueIterator.next());
        assertEquals("two", denseValueIterator.next());
        assertFalse(denseValueIterator.hasNext());
        assertThrows(NoSuchElementException.class, denseValueIterator::next);

        String[] copied = denseValues.toArray(new String[0]);
        assertEquals("zero", copied[0]);
        assertEquals("two", copied[1]);
    }

    @Test
    void validatesKeysCapacitiesAndDenseIndices() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntSparseMap<String>(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntSparseMap<String>(1, -1));

        OrderedIntSparseMap<String> map = new OrderedIntSparseMap<String>(0, 0);
        assertThrows(IllegalArgumentException.class, () -> map.put(-1, "negative"));
        assertThrows(IllegalArgumentException.class,
                () -> map.put(Integer.MAX_VALUE, "too-large"));
        assertThrows(IllegalArgumentException.class, () -> map.ensureKeyCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> map.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> map.putAll(null));
        assertThrows(IndexOutOfBoundsException.class, () -> map.keyAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> map.valueAt(-1));
        assertThrows(NoSuchElementException.class, () -> map.denseValues().first());
        assertThrows(NoSuchElementException.class, () -> map.denseValues().peek());
    }

    private static void assertOrderedKeys(OrderedIntSparseMap<?> map, int... expected) {
        IntIterator iterator = map.keys().iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertEquals(expected[i], iterator.nextInt());
        }
        assertFalse(iterator.hasNext());
    }

    private static void assertOrderedValues(OrderedIntSparseMap<String> map,
            String... expected) {
        ObjectIterator<String> iterator = map.values().iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertEquals(expected[i], iterator.next());
        }
        assertFalse(iterator.hasNext());
    }
}
