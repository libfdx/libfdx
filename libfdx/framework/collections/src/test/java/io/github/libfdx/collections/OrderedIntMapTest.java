package io.github.libfdx.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.jupiter.api.Test;

class OrderedIntMapTest {
    @Test
    void supportsFullIntRangeNullValuesAndStableReplacementOrder() {
        OrderedIntMap<String> map = new OrderedIntMap<String>(2, 0.6f);
        map.put(Integer.MIN_VALUE, "min");
        map.put(-1, "negative");
        map.put(0, null);
        map.put(Integer.MAX_VALUE, "max");

        assertEquals(4, map.size());
        assertEquals("min", map.get(Integer.MIN_VALUE));
        assertEquals("fallback", map.get(9, "fallback"));
        assertTrue(map.containsKey(0));
        assertTrue(map.containsValue(null));
        assertEquals(Integer.MIN_VALUE, map.firstKey());
        assertEquals(Integer.MAX_VALUE, map.lastKey());

        assertEquals("negative", map.put(-1, "replaced"));
        assertOrderedKeys(map, Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE);
        assertOrderedValues(map, "min", "replaced", null, "max");
    }

    @Test
    void denseTailSwapRepairsLookupAndPreservesLogicalOrder() {
        OrderedIntMap<String> map = new OrderedIntMap<String>(4);
        map.put(10, "ten");
        map.put(20, "twenty");
        map.put(30, "thirty");
        map.put(40, "forty");

        assertEquals("twenty", map.remove(20));

        assertEquals(3, map.size());
        assertFalse(map.containsKey(20));
        assertEquals("forty", map.get(40));
        assertEquals(40, map.keyAt(1));
        assertEquals("forty", map.valueAt(1));
        assertOrderedKeys(map, 10, 30, 40);

        assertEquals("ten", map.remove(10));
        assertEquals("forty", map.remove(40));
        assertOrderedKeys(map, 30);
        map.put(20, "again");
        assertOrderedKeys(map, 30, 20);
    }

    @Test
    void movesLogicalOrderWithoutChangingDenseStorage() {
        OrderedIntMap<String> map = new OrderedIntMap<String>(3);
        map.put(-7, "seven");
        map.put(0, "zero");
        map.put(9, "nine");

        map.moveToFirst(9).moveToLast(-7);

        assertOrderedKeys(map, 9, 0, -7);
        assertEquals(-7, map.keyAt(0));
        assertEquals(0, map.keyAt(1));
        assertEquals(9, map.keyAt(2));
        assertEquals(0, map.findKey("zero", 99));
        assertEquals("zero", map.remove(0));
        assertOrderedKeys(map, 9, -7);
        assertEquals("seven", map.get(-7));
        assertThrows(IllegalArgumentException.class, () -> map.moveToFirst(100));
        assertThrows(IllegalArgumentException.class, () -> map.moveToLast(100));
    }

    @Test
    void cachesViewsIteratorsAndMutableEntry() {
        OrderedIntMap<String> map = new OrderedIntMap<String>();
        map.put(4, "four");
        map.put(-2, "two");

        ObjectIterable<OrderedIntMap.Entry<String>> entries = map.entries();
        ObjectIterator<OrderedIntMap.Entry<String>> entryIterator = entries.iterator();
        assertSame(entries, map.entries());
        assertSame(entryIterator, entries.iterator());
        OrderedIntMap.Entry<String> entry = entryIterator.next();
        assertEquals(4, entry.key());
        assertSame(entry, entryIterator.next());
        assertEquals(-2, entry.key());
        assertFalse(entryIterator.hasNext());
        assertNull(entry.value());
        assertThrows(NoSuchElementException.class, entryIterator::next);

        IntIterable keys = map.keys();
        IntIterator keyIterator = keys.iterator();
        assertSame(keys, map.keys());
        assertSame(keyIterator, keys.iterator());
        assertEquals(4, keyIterator.nextInt());
        assertEquals(-2, keyIterator.nextInt());

        ObjectIterable<String> values = map.values();
        ObjectIterator<String> valueIterator = values.iterator();
        assertSame(values, map.values());
        assertSame(valueIterator, values.iterator());
        assertEquals("four", valueIterator.next());
        assertEquals("two", valueIterator.next());

        IntMapView<String> view = map.view();
        assertSame(view, map.view());
        assertEquals("two", view.get(-2));
        assertSame(entries, view.entries());
        assertSame(keys, view.keys());
        assertSame(values, view.values());
    }

    @Test
    void copiesPrimitiveMapViewsAndRetainsStorageAcrossClear() {
        IntMap<String> unordered = new IntMap<String>();
        unordered.put(-5, "negative");
        unordered.put(8, "eight");
        OrderedIntMap<String> map = new OrderedIntMap<String>(unordered.view());

        assertEquals("negative", map.get(-5));
        assertEquals("eight", map.get(8));

        int tableCapacity = map.capacity();
        int denseCapacity = map.denseCapacity();
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(tableCapacity, map.capacity());
        assertEquals(denseCapacity, map.denseCapacity());
        assertThrows(NoSuchElementException.class, map::firstKey);
        assertThrows(NoSuchElementException.class, map::lastKey);

        map.ensureCapacity(20);
        assertTrue(map.denseCapacity() >= 20);
        map.put(Integer.MIN_VALUE, "min");
        map.shrink();
        assertEquals(1, map.denseCapacity());
        assertEquals("min", map.get(Integer.MIN_VALUE));
    }

    @Test
    void randomizedMutationMatchesLinkedHashMap() {
        OrderedIntMap<Integer> actual = new OrderedIntMap<Integer>(0, 0.55f);
        LinkedHashMap<Integer, Integer> expected = new LinkedHashMap<Integer, Integer>();
        Random random = new Random(0x1A2B3C4DL);

        for (int operation = 0; operation < 20_000; operation++) {
            int key = randomKey(random, operation);
            int kind = random.nextInt(4);
            if (kind <= 1) {
                Integer value = operation;
                assertEquals(expected.put(key, value), actual.put(key, value));
            }
            else if (kind == 2) {
                assertEquals(expected.remove(key), actual.remove(key));
            }
            else {
                assertEquals(expected.get(key), actual.get(key));
                assertEquals(expected.containsKey(key), actual.containsKey(key));
            }
            if ((operation & 63) == 0) {
                assertMatches(expected, actual);
            }
        }
        assertMatches(expected, actual);
    }

    @Test
    void validatesCapacityLoadFactorAndDenseIndices() {
        assertThrows(IllegalArgumentException.class, () -> new OrderedIntMap<String>(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedIntMap<String>(1, 0.0f));
        OrderedIntMap<String> map = new OrderedIntMap<String>(0);
        assertThrows(IllegalArgumentException.class, () -> map.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> map.putAll(null));
        assertThrows(IndexOutOfBoundsException.class, () -> map.keyAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> map.valueAt(-1));
    }

    private static int randomKey(Random random, int operation) {
        if ((operation & 127) == 0) {
            return Integer.MIN_VALUE;
        }
        if ((operation & 127) == 1) {
            return Integer.MAX_VALUE;
        }
        return random.nextInt(401) - 200;
    }

    private static void assertMatches(Map<Integer, Integer> expected,
            OrderedIntMap<Integer> actual) {
        assertEquals(expected.size(), actual.size());
        IntIterator iterator = actual.keys().iterator();
        int denseCount = 0;
        for (Map.Entry<Integer, Integer> entry : expected.entrySet()) {
            assertTrue(iterator.hasNext());
            int key = iterator.nextInt();
            assertEquals(entry.getKey().intValue(), key);
            assertEquals(entry.getValue(), actual.get(key));
        }
        assertFalse(iterator.hasNext());
        for (int i = 0; i < actual.size(); i++) {
            int key = actual.keyAt(i);
            assertTrue(expected.containsKey(key));
            assertEquals(expected.get(key), actual.valueAt(i));
            denseCount++;
        }
        assertEquals(expected.size(), denseCount);
    }

    private static void assertOrderedKeys(OrderedIntMap<?> map, int... expected) {
        IntIterator iterator = map.keys().iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertEquals(expected[i], iterator.nextInt());
        }
        assertFalse(iterator.hasNext());
    }

    private static void assertOrderedValues(OrderedIntMap<String> map,
            String... expected) {
        ObjectIterator<String> iterator = map.values().iterator();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(iterator.hasNext());
            assertEquals(expected[i], iterator.next());
        }
        assertFalse(iterator.hasNext());
    }
}
