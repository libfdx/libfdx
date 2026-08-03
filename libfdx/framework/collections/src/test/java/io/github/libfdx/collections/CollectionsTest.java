package io.github.libfdx.collections;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the collections test scenario.
 *
 * @author xpenatan
 */
final class CollectionsTest {
    @Test
    void arrayGrowsAndPreservesOrderWhenOrdered() {
        Array<String> array = new Array<String>(true, 1);

        array.add("a").add("b").add("c");

        assertEquals(3, array.size());
        assertTrue(array.capacity() >= 3);
        assertEquals("a", array.get(0));
        assertEquals("b", array.removeIndex(1));
        assertEquals("c", array.get(1));
        assertTrue(array.removeValue("a"));
        assertEquals("c", array.get(0));
    }

    @Test
    void arrayCanRemoveWithoutPreservingOrder() {
        Array<String> array = new Array<String>(false, 2);
        array.add("a").add("b").add("c");

        assertEquals("a", array.removeIndex(0));

        assertEquals(2, array.size());
        assertEquals("c", array.get(0));
        assertEquals("b", array.get(1));
    }

    @Test
    void arrayViewIsReadOnlyLiveAndCached() {
        Array<String> array = new Array<String>(1);
        ArrayView<String> view = array.view();

        assertSame(view, array.view());
        assertTrue(view.isEmpty());
        array.add("first").add("second");

        assertEquals(2, view.size());
        assertEquals("first", view.first());
        assertEquals("second", view.peek());
        assertArrayEquals(new Object[] { "first", "second" }, view.toArray());
    }

    @Test
    void arrayFactoriesAndSelfAppendPreserveTheOriginalValuesOnce() {
        ArrayView<String> empty = Array.emptyView();
        ArrayView<String> secondEmpty = Array.emptyView();
        assertSame(empty, secondEmpty);
        assertTrue(empty.isEmpty());

        Array<String> direct = Array.of("a", "b");
        direct.addAll(direct);
        assertArrayEquals(new String[] { "a", "b", "a", "b" }, direct.toArray(new String[0]));

        Array<String> throughView = Array.of("c", "d");
        throughView.addAll(throughView.view());
        assertArrayEquals(new String[] { "c", "d", "c", "d" }, throughView.toArray(new String[0]));

        Array<String> copy = new Array<String>(throughView.view());
        assertArrayEquals(throughView.toArray(), copy.toArray());
        assertThrows(IllegalArgumentException.class, () -> direct.addAll((Array<String>)null));
        assertThrows(IllegalArgumentException.class, () -> direct.addAll((ArrayView<String>)null));
    }

    @Test
    void arraySortsAndCopiesIntoTypedArrays() {
        Array<String> array = new Array<String>(3);
        array.add("charlie").add("alpha").add("bravo");

        array.sort();
        assertArrayEquals(new String[] { "alpha", "bravo", "charlie" }, array.toArray(new String[0]));

        String[] destination = new String[] { "old", "old", "old", "old", "untouched" };
        assertSame(destination, array.toArray(destination));
        assertArrayEquals(new String[] { "alpha", "bravo", "charlie", null, "untouched" }, destination);

        array.sort((left, right) -> right.compareTo(left));
        assertArrayEquals(new String[] { "charlie", "bravo", "alpha" }, array.toArray(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> array.toArray(null));
    }

    @Test
    void arrayInsertsSwapsAndTruncates() {
        Array<String> ordered = new Array<String>(true, 2);
        ordered.add("a").add("c");
        ordered.insert(1, "b").insert(3, "d");

        assertArrayEquals(new Object[] { "a", "b", "c", "d" }, ordered.toArray());
        ordered.swap(0, 2).truncate(2);
        assertArrayEquals(new Object[] { "c", "b" }, ordered.toArray());
        ordered.truncate(8);
        assertArrayEquals(new Object[] { "c", "b" }, ordered.toArray());

        Array<String> unordered = new Array<String>(false, 3);
        unordered.add("a").add("b").add("c");
        unordered.insert(0, "z");

        assertArrayEquals(new Object[] { "z", "b", "c", "a" }, unordered.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> ordered.insert(3, "bad"));
        assertThrows(IndexOutOfBoundsException.class, () -> ordered.swap(0, 3));
        assertThrows(IllegalArgumentException.class, () -> ordered.truncate(-1));
    }

    @Test
    void arrayRemovesRangesAndReverses() {
        Array<String> ordered = new Array<String>(true, 5);
        ordered.add("a").add("b").add("c").add("d").add("e");

        ordered.removeRange(1, 3);
        assertArrayEquals(new Object[] { "a", "e" }, ordered.toArray());
        ordered.reverse();
        assertArrayEquals(new Object[] { "e", "a" }, ordered.toArray());

        Array<String> unordered = new Array<String>(false, 6);
        unordered.add("a").add("b").add("c").add("d").add("e").add("f");

        unordered.removeRange(1, 3);
        assertArrayEquals(new Object[] { "a", "e", "f" }, unordered.toArray());
        unordered.reverse();
        assertArrayEquals(new Object[] { "f", "e", "a" }, unordered.toArray());

        assertThrows(IndexOutOfBoundsException.class, () -> ordered.removeRange(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> ordered.removeRange(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> ordered.removeRange(0, 2));
    }

    @Test
    void arrayLookupCanUseEqualityOrIdentity() {
        String first = new String("same");
        String second = new String("same");
        String probe = new String("same");
        Array<String> array = new Array<String>(true, 4);
        array.add(first).add(null).add(second).add(first);

        assertTrue(array.contains(probe));
        assertFalse(array.contains(probe, true));
        assertEquals(0, array.indexOf(probe));
        assertEquals(-1, array.indexOf(probe, true));
        assertEquals(3, array.lastIndexOf(probe));
        assertEquals(3, array.lastIndexOf(first, true));
        assertEquals(1, array.lastIndexOf(null, true));
        assertFalse(array.removeValue(probe, true));
        assertTrue(array.removeValue(probe));
        assertArrayEquals(new Object[] { null, second, first }, array.toArray());
        assertTrue(array.removeValue(first, true));
        assertArrayEquals(new Object[] { null, second }, array.toArray());
    }

    @Test
    void arrayRemovesFirstMatchForEachSuppliedValue() {
        String first = new String("a");
        String second = new String("a");
        String third = new String("b");
        Array<String> array = new Array<String>(true, 4);
        array.add(first).add(third).add(second).add("c");
        Array<String> values = new Array<String>(2);
        values.add(new String("a")).add(third);

        assertTrue(array.removeAll(values));
        assertArrayEquals(new Object[] { second, "c" }, array.toArray());
        assertTrue(array.removeAll(values));
        assertArrayEquals(new Object[] { "c" }, array.toArray());
        assertFalse(array.removeAll(values));

        Array<String> identityArray = new Array<String>(true, 3);
        identityArray.add(first).add(new String("b")).add(second);
        Array<String> identityValues = new Array<String>(2);
        identityValues.add(new String("a")).add(second);

        assertTrue(identityArray.removeAll(identityValues, true));
        assertArrayEquals(new Object[] { first, "b" }, identityArray.toArray());
        assertTrue(identityArray.removeAll(identityArray));
        assertTrue(identityArray.isEmpty());
    }

    @Test
    void arrayStackHelpersUseFrontAndBackWithoutExtraStorage() {
        Array<String> array = new Array<String>(2);
        array.add("a").add("b");

        assertTrue(array.notEmpty());
        assertEquals("a", array.first());
        assertEquals("b", array.peek());
        assertEquals("b", array.pop());
        assertEquals(1, array.size());
        assertEquals("a", array.peek());
        assertEquals("a", array.pop());
        assertTrue(array.isEmpty());
        assertFalse(array.notEmpty());
        assertThrows(NoSuchElementException.class, array::first);
        assertThrows(NoSuchElementException.class, array::peek);
        assertThrows(NoSuchElementException.class, array::pop);
    }

    @Test
    void intArrayGrowsRemovesAndExportsCopy() {
        IntArray array = new IntArray(true, 1);
        array.add(1).add(2).add(3);

        assertEquals(3, array.size());
        assertTrue(array.capacity() >= 3);
        assertEquals(2, array.set(1, 7));
        assertTrue(array.contains(7));
        assertEquals(1, array.indexOf(7));
        assertEquals(1, array.removeIndex(0));
        assertArrayEquals(new int[] { 7, 3 }, array.toArray());
        array.clear();
        assertTrue(array.isEmpty());
        array.shrink();
        assertEquals(1, array.capacity());
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(0));
    }

    @Test
    void booleanArraySupportsOrderedAndUnorderedOperations() {
        BooleanArray ordered = new BooleanArray(true, 1);
        ordered.add(true).add(false).insert(1, true);

        assertArrayEquals(new boolean[] { true, true, false }, ordered.toArray());
        assertEquals(1, ordered.lastIndexOf(true));
        assertTrue(ordered.removeValue(false));
        assertArrayEquals(new boolean[] { true, true }, ordered.toArray());
        assertTrue(ordered.removeAll(new BooleanArray().add(true)));
        assertArrayEquals(new boolean[] { true }, ordered.toArray());

        BooleanArray unordered = new BooleanArray(false, 2);
        unordered.add(true).add(false).add(false);
        assertTrue(unordered.removeIndex(0));
        assertArrayEquals(new boolean[] { false, false }, unordered.toArray());
        unordered.swap(0, 1).reverse().truncate(1);
        assertEquals(1, unordered.size());
        assertFalse(unordered.first());
    }

    @Test
    void booleanArrayIteratorIsPrimitiveAndReusable() {
        BooleanArray values = new BooleanArray().add(true).add(false);
        BooleanIterator first = values.iterator();

        assertTrue(first.nextBoolean());
        assertFalse(first.nextBoolean());
        assertFalse(first.hasNext());
        assertThrows(NoSuchElementException.class, first::nextBoolean);

        BooleanIterator second = values.iterator();
        assertSame(first, second);
        assertTrue(second.nextBoolean());
    }

    @Test
    void intArrayCanRemoveWithoutPreservingOrder() {
        IntArray array = new IntArray(false, 2);
        array.add(10).add(20).add(30);

        assertEquals(10, array.removeIndex(0));

        assertArrayEquals(new int[] { 30, 20 }, array.toArray());
        assertFalse(array.removeValue(99));
    }

    @Test
    void primitiveArraysInsertSwapAndTruncate() {
        IntArray ints = new IntArray(true, 2);
        ints.add(1).add(3).insert(1, 2).insert(3, 4);
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, ints.toArray());
        ints.swap(0, 3).truncate(2);
        assertArrayEquals(new int[] { 4, 2 }, ints.toArray());
        ints.truncate(9);
        assertArrayEquals(new int[] { 4, 2 }, ints.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> ints.insert(3, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> ints.swap(0, 2));
        assertThrows(IllegalArgumentException.class, () -> ints.truncate(-1));

        FloatArray floats = new FloatArray(false, 3);
        floats.add(1.0f).add(2.0f).add(3.0f).insert(0, -1.0f);
        assertArrayEquals(new float[] { -1.0f, 2.0f, 3.0f, 1.0f }, floats.toArray());
        floats.swap(1, 3).truncate(3);
        assertArrayEquals(new float[] { -1.0f, 1.0f, 3.0f }, floats.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> floats.insert(-1, 0.0f));

        LongArray longs = new LongArray(true, 1);
        longs.add(4L).insert(0, 2L).insert(2, 6L).swap(0, 2).truncate(2);
        assertArrayEquals(new long[] { 6L, 4L }, longs.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> longs.swap(-1, 0));
    }

    @Test
    void primitiveArraysRemoveRangesAndReverse() {
        IntArray ints = new IntArray(true, 6);
        ints.add(1).add(2).add(3).add(4).add(5).add(6);
        ints.removeRange(2, 4).reverse();
        assertArrayEquals(new int[] { 6, 2, 1 }, ints.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> ints.removeRange(-1, 0));

        FloatArray floats = new FloatArray(false, 6);
        floats.add(1.0f).add(2.0f).add(3.0f).add(4.0f).add(5.0f).add(6.0f);
        floats.removeRange(1, 3).reverse();
        assertArrayEquals(new float[] { 6.0f, 5.0f, 1.0f }, floats.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> floats.removeRange(2, 1));

        LongArray longs = new LongArray(true, 5);
        longs.add(10L).add(20L).add(30L).add(40L).add(50L);
        longs.removeRange(0, 1).reverse();
        assertArrayEquals(new long[] { 50L, 40L, 30L }, longs.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> longs.removeRange(1, 3));
    }

    @Test
    void primitiveArraysFindLastAndRemoveAll() {
        IntArray ints = new IntArray(true, 5);
        ints.add(1).add(2).add(3).add(2).add(4);
        IntArray intValues = new IntArray(2);
        intValues.add(2).add(4);
        assertEquals(3, ints.lastIndexOf(2));
        assertTrue(ints.removeAll(intValues));
        assertArrayEquals(new int[] { 1, 3, 2 }, ints.toArray());
        assertTrue(ints.removeAll(intValues));
        assertArrayEquals(new int[] { 1, 3 }, ints.toArray());
        assertFalse(ints.removeAll(intValues));
        assertTrue(ints.removeAll(ints));
        assertTrue(ints.isEmpty());

        FloatArray floats = new FloatArray(true, 4);
        floats.add(0.0f).add(-0.0f).add(Float.NaN).add(Float.intBitsToFloat(0x7fc00001));
        FloatArray floatValues = new FloatArray(2);
        floatValues.add(-0.0f).add(Float.NaN);
        assertEquals(1, floats.lastIndexOf(-0.0f));
        assertEquals(3, floats.lastIndexOf(Float.intBitsToFloat(0x7fc00002)));
        assertTrue(floats.removeAll(floatValues));
        assertArrayEquals(new float[] { 0.0f, Float.intBitsToFloat(0x7fc00001) }, floats.toArray());

        LongArray longs = new LongArray(true, 5);
        longs.add(7L).add(9L).add(7L).add(11L).add(13L);
        LongArray longValues = new LongArray(2);
        longValues.add(7L).add(13L);
        assertEquals(2, longs.lastIndexOf(7L));
        assertTrue(longs.removeAll(longValues));
        assertArrayEquals(new long[] { 9L, 7L, 11L }, longs.toArray());
    }

    @Test
    void primitiveArrayStackHelpersUsePrimitiveValues() {
        IntArray ints = new IntArray(2);
        ints.add(4).add(9);
        assertTrue(ints.notEmpty());
        assertEquals(4, ints.first());
        assertEquals(9, ints.peek());
        assertEquals(9, ints.pop());
        assertArrayEquals(new int[] { 4 }, ints.toArray());
        ints.pop();
        assertFalse(ints.notEmpty());
        assertThrows(NoSuchElementException.class, ints::first);
        assertThrows(NoSuchElementException.class, ints::peek);
        assertThrows(NoSuchElementException.class, ints::pop);

        FloatArray floats = new FloatArray(2);
        floats.add(1.25f).add(-2.5f);
        assertTrue(floats.notEmpty());
        assertEquals(1.25f, floats.first());
        assertEquals(-2.5f, floats.peek());
        assertEquals(-2.5f, floats.pop());
        assertArrayEquals(new float[] { 1.25f }, floats.toArray());
        floats.pop();
        assertFalse(floats.notEmpty());
        assertThrows(NoSuchElementException.class, floats::first);
        assertThrows(NoSuchElementException.class, floats::peek);
        assertThrows(NoSuchElementException.class, floats::pop);

        LongArray longs = new LongArray(2);
        longs.add(11L).add(17L);
        assertTrue(longs.notEmpty());
        assertEquals(11L, longs.first());
        assertEquals(17L, longs.peek());
        assertEquals(17L, longs.pop());
        assertArrayEquals(new long[] { 11L }, longs.toArray());
        longs.pop();
        assertFalse(longs.notEmpty());
        assertThrows(NoSuchElementException.class, longs::first);
        assertThrows(NoSuchElementException.class, longs::peek);
        assertThrows(NoSuchElementException.class, longs::pop);
    }

    @Test
    void floatArrayUsesFloatBitIdentity() {
        FloatArray array = new FloatArray(true, 1);
        array.add(0.0f).add(-0.0f).add(Float.NaN);

        assertEquals(0, array.indexOf(0.0f));
        assertEquals(1, array.indexOf(-0.0f));
        assertEquals(2, array.indexOf(Float.intBitsToFloat(0x7fc00001)));
        assertTrue(array.removeValue(Float.NaN));
        assertArrayEquals(new float[] { 0.0f, -0.0f }, array.toArray());
    }

    @Test
    void longArrayGrowsAndRemovesValues() {
        LongArray array = new LongArray(false, 1);
        long large = 0x7000_0000_0000_0001L;

        array.add(large).add(-large).add(42L);

        assertEquals(-large, array.get(1));
        assertEquals(large, array.removeIndex(0));
        assertArrayEquals(new long[] { 42L, -large }, array.toArray());
        assertTrue(array.removeValue(-large));
        assertArrayEquals(new long[] { 42L }, array.toArray());
    }

    @Test
    void objectMapStoresNullValuesAndReusesRemovedSlots() {
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(2);
        Key one = new Key(1);
        Key two = new Key(2);
        Key three = new Key(3);

        assertNull(map.put(one, "one"));
        assertNull(map.put(two, null));
        assertEquals("one", map.put(one, "uno"));
        assertEquals("uno", map.get(one));
        assertTrue(map.containsKey(two));
        assertNull(map.get(two));
        assertNull(map.remove(two));
        assertFalse(map.containsKey(two));
        map.put(three, "three");

        assertEquals(2, map.size());
        assertEquals("three", map.get(three));
        assertThrows(IllegalArgumentException.class, () -> map.put(null, "bad"));
    }

    @Test
    void objectMapFindsValuesByEqualsIdentityAndNull() {
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(2);
        Key one = new Key(1);
        Key two = new Key(2);
        String stored = new String("value");
        String equal = new String("value");
        map.put(one, stored);
        map.put(two, null);

        assertTrue(map.notEmpty());
        assertTrue(map.containsValue(equal));
        assertFalse(map.containsValue(equal, true));
        assertTrue(map.containsValue(stored, true));
        assertTrue(map.containsValue(null));
        assertFalse(map.containsValue("missing"));
        assertEquals(one, map.findKey(equal));
        assertNull(map.findKey(equal, true));
        assertEquals(one, map.findKey(stored, true));
        assertEquals(two, map.findKey(null));
        map.clear();
        assertTrue(map.isEmpty());
        assertFalse(map.notEmpty());
    }

    @Test
    void objectMapEntriesSkipRemovedSlotsAndExposeNullValues() {
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(2);
        Key one = new Key(1);
        Key two = new Key(2);
        Key three = new Key(3);
        map.put(one, "one");
        map.put(two, null);
        map.put(three, "three");
        map.remove(one);

        boolean sawTwo = false;
        boolean sawThree = false;
        int count = 0;
        ObjectIterator<ObjectMap.Entry<Key, String>> entryIterator = map.entries().iterator();
        while (entryIterator.hasNext()) {
            ObjectMap.Entry<Key, String> entry = entryIterator.next();
            count++;
            if (entry.key().value == 2) {
                sawTwo = true;
                assertNull(entry.value());
            }
            if (entry.key().value == 3) {
                sawThree = true;
                assertEquals("three", entry.value());
            }
        }

        assertEquals(2, count);
        assertTrue(sawTwo);
        assertTrue(sawThree);
        ObjectIterator<ObjectMap.Entry<Key, String>> iterator = map.entries().iterator();
        iterator.next();
        iterator.next();
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void objectMapKeysAndValuesSkipRemovedSlots() {
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(2);
        Key one = new Key(1);
        Key two = new Key(2);
        Key three = new Key(3);
        map.put(one, "one");
        map.put(two, null);
        map.put(three, "three");
        map.remove(one);

        int keySum = 0;
        int keyCount = 0;
        ObjectIterator<Key> keyIterator = map.keys().iterator();
        while (keyIterator.hasNext()) {
            Key key = keyIterator.next();
            keySum += key.value;
            keyCount++;
        }

        int valueCount = 0;
        boolean sawNull = false;
        boolean sawThree = false;
        ObjectIterator<String> valueIterator = map.values().iterator();
        while (valueIterator.hasNext()) {
            String value = valueIterator.next();
            valueCount++;
            if (value == null) {
                sawNull = true;
            }
            if ("three".equals(value)) {
                sawThree = true;
            }
        }

        assertEquals(5, keySum);
        assertEquals(2, keyCount);
        assertEquals(2, valueCount);
        assertTrue(sawNull);
        assertTrue(sawThree);
        ObjectIterator<Key> keys = map.keys().iterator();
        keys.next();
        keys.next();
        assertThrows(NoSuchElementException.class, keys::next);
        ObjectIterator<String> values = map.values().iterator();
        values.next();
        values.next();
        assertThrows(NoSuchElementException.class, values::next);
    }

    @Test
    void objectMapRehashesTombstonesBeforeProbeSentinelIsLost() {
        int mask = 3;
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(3, 0.75f);
        Key slot0 = new Key(10, intHashForSlot(0, mask));
        Key slot1 = new Key(11, intHashForSlot(1, mask));
        Key slot2 = new Key(12, intHashForSlot(2, mask));
        Key slot3 = new Key(13, intHashForSlot(3, mask));
        Key missing = new Key(14, intHashForSlot(0, mask));

        map.put(slot0, "zero");
        map.put(slot1, "one");
        map.put(slot2, "two");
        assertEquals("zero", map.remove(slot0));
        map.put(slot3, "three");

        assertEquals(3, map.size());
        assertEquals("missing", map.get(missing, "missing"));
        assertFalse(map.containsKey(missing));
    }

    @Test
    void objectMapViewIsReadOnlyLiveAndCached() {
        ObjectMap<Key, String> map = new ObjectMap<Key, String>(1);
        ObjectMapView<Key, String> view = map.view();
        Key key = new Key(7);

        assertSame(view, map.view());
        assertTrue(view.isEmpty());
        map.put(key, "seven");

        assertEquals(1, view.size());
        assertTrue(view.containsKey(key));
        assertEquals("seven", view.get(key));

        ObjectMap<Key, String> copy = new ObjectMap<Key, String>(view);
        assertEquals("seven", copy.get(key));
    }

    @Test
    void objectMapIdentityComparisonKeepsEqualButDistinctKeysSeparate() {
        String first = new String("same");
        String second = new String("same");
        ObjectMap<String, Integer> map =
                new ObjectMap<String, Integer>(1, KeyComparison.IDENTITY);

        map.put(first, 1);
        map.put(second, 2);

        assertEquals(KeyComparison.IDENTITY, map.keyComparison());
        assertEquals(2, map.size());
        assertEquals(1, map.get(first));
        assertEquals(2, map.get(second));
        assertNull(map.get(new String("same")));

        ObjectMap<String, Integer> identityCopy =
                new ObjectMap<String, Integer>(map.view(), KeyComparison.IDENTITY);
        assertEquals(2, identityCopy.size());
        assertEquals(1, identityCopy.get(first));
        assertEquals(2, identityCopy.get(second));

        assertEquals(1, map.remove(first));
        assertFalse(map.containsKey(first));
        assertEquals(2, map.get(second));

        ObjectMap<String, Integer> equality = new ObjectMap<String, Integer>();
        equality.put(first, 1);
        equality.put(second, 2);
        assertEquals(KeyComparison.EQUALITY, equality.keyComparison());
        assertEquals(1, equality.size());
        assertEquals(2, equality.get(first));
        assertThrows(IllegalArgumentException.class,
                () -> new ObjectMap<String, Integer>(1, null));
    }

    @Test
    void primitiveMapViewsAreCachedLiveAndCopyable() {
        IntMap<String> ints = new IntMap<String>();
        IntMapView<String> intView = ints.view();
        assertSame(intView, ints.view());
        assertSame(ints.entries(), ints.entries());
        assertSame(ints.keys(), ints.keys());
        assertSame(ints.values(), ints.values());
        ints.put(7, "seven");
        assertEquals("seven", intView.get(7));
        assertEquals("seven", new IntMap<String>(intView).get(7));

        LongMap<String> longs = new LongMap<String>();
        LongMapView<String> longView = longs.view();
        assertSame(longView, longs.view());
        assertSame(longs.entries(), longs.entries());
        assertSame(longs.keys(), longs.keys());
        assertSame(longs.values(), longs.values());
        longs.put(9_000_000_000L, "wide");
        assertEquals("wide", longView.get(9_000_000_000L));
        assertEquals("wide", new LongMap<String>(longView).get(9_000_000_000L));
    }

    @Test
    void setsHandleCollisionsRemovalAndPrimitiveIteration() {
        ObjectSet<Key> objects = new ObjectSet<Key>(2);
        Key first = new Key(1, 0);
        Key second = new Key(2, 0);
        Key third = new Key(3, 0);
        assertTrue(objects.add(first));
        assertTrue(objects.add(second));
        assertFalse(objects.add(new Key(1, 0)));
        assertTrue(objects.remove(first));
        assertTrue(objects.add(third));
        int objectSum = 0;
        ObjectIterator<Key> objectIterator = objects.iterator();
        while (objectIterator.hasNext()) {
            Key value = objectIterator.next();
            objectSum += value.value;
        }
        assertEquals(5, objectSum);
        assertEquals(2, objects.size());

        IntSet ints = new IntSet(2);
        for (int i = 0; i < 128; i++) {
            assertTrue(ints.add(i));
        }
        for (int i = 0; i < 128; i += 2) {
            assertTrue(ints.remove(i));
        }
        long intSum = 0L;
        int intCount = 0;
        IntIterator iterator = ints.iterator();
        while (iterator.hasNext()) {
            int value = iterator.nextInt();
            assertTrue((value & 1) != 0);
            intSum += value;
            intCount++;
        }
        assertEquals(64, intCount);
        assertEquals(4096L, intSum);
        assertThrows(NoSuchElementException.class, iterator::nextInt);
    }

    @Test
    void libfdxIterablesDoNotDependOnJavaIterationContracts() {
        assertFalse(Iterable.class.isAssignableFrom(ObjectIterable.class));
        assertFalse(Iterable.class.isAssignableFrom(IntIterable.class));
        assertFalse(Iterable.class.isAssignableFrom(LongIterable.class));
        assertFalse(Iterable.class.isAssignableFrom(FloatIterable.class));
        assertFalse(Iterator.class.isAssignableFrom(ObjectIterator.class));
        assertFalse(Iterator.class.isAssignableFrom(IntIterator.class));
        assertFalse(Iterator.class.isAssignableFrom(LongIterator.class));
        assertFalse(Iterator.class.isAssignableFrom(FloatIterator.class));

        IntArray ints = new IntArray().add(3).add(5);
        IntIterator intIterator = ints.iterator();
        assertEquals(3, intIterator.nextInt());
        assertSame(intIterator, ints.iterator());
        assertEquals(3, intIterator.nextInt());

        LongArray longs = new LongArray().add(7L).add(11L);
        LongIterator longIterator = longs.iterator();
        assertEquals(7L, longIterator.nextLong());
        assertSame(longIterator, longs.iterator());
        assertEquals(7L, longIterator.nextLong());

        FloatArray floats = new FloatArray().add(1.5f).add(2.5f);
        FloatIterator floatIterator = floats.iterator();
        assertEquals(Float.floatToIntBits(1.5f), Float.floatToIntBits(floatIterator.nextFloat()));
        assertSame(floatIterator, floats.iterator());
        assertEquals(Float.floatToIntBits(1.5f), Float.floatToIntBits(floatIterator.nextFloat()));
    }

    @Test
    void collectionIterablesReuseTheirIteratorInstances() {
        Array<Integer> array = Array.of(1, 2);
        ObjectSet<Integer> set = new ObjectSet<Integer>();
        set.add(1);
        ObjectQueue<Integer> queue = new ObjectQueue<Integer>();
        queue.addLast(1);
        ObjectLinkedList<Integer> list = new ObjectLinkedList<Integer>();
        list.addLast(1);

        assertReusableIterator(array);
        assertReusableIterator(array.view());
        assertReusableIterator(set);
        assertReusableIterator(queue);
        assertReusableIterator(list);

        ObjectMap<Integer, Integer> objects = new ObjectMap<Integer, Integer>();
        objects.put(1, 1);
        objects.put(2, 2);
        assertReusableIterator(objects.entries());
        assertReusableIterator(objects.keys());
        assertReusableIterator(objects.values());
        ObjectIterator<ObjectMap.Entry<Integer, Integer>> objectEntries = objects.entries().iterator();
        assertSame(objectEntries.next(), objectEntries.next());

        OrderedMap<Integer, Integer> ordered = new OrderedMap<Integer, Integer>();
        ordered.put(1, 1);
        ordered.put(2, 2);
        assertReusableIterator(ordered.entries());
        assertReusableIterator(ordered.keys());
        assertReusableIterator(ordered.values());
        ObjectIterator<OrderedMap.Entry<Integer, Integer>> orderedEntries = ordered.entries().iterator();
        assertSame(orderedEntries.next(), orderedEntries.next());

        IntMap<Integer> ints = new IntMap<Integer>();
        ints.put(1, 1);
        ints.put(2, 2);
        assertReusableIterator(ints.entries());
        assertReusableIterator(ints.keys());
        assertReusableIterator(ints.values());
        ObjectIterator<IntMap.Entry<Integer>> intEntries = ints.entries().iterator();
        assertSame(intEntries.next(), intEntries.next());

        LongMap<Integer> longs = new LongMap<Integer>();
        longs.put(1L, 1);
        longs.put(2L, 2);
        assertReusableIterator(longs.entries());
        assertReusableIterator(longs.keys());
        assertReusableIterator(longs.values());
        ObjectIterator<LongMap.Entry<Integer>> longEntries = longs.entries().iterator();
        assertSame(longEntries.next(), longEntries.next());

        FloatMap<Integer> floats = new FloatMap<Integer>();
        floats.put(1.0f, 1);
        floats.put(2.0f, 2);
        assertReusableIterator(floats.entries());
        assertReusableIterator(floats.keys());
        assertReusableIterator(floats.values());
        ObjectIterator<FloatMap.Entry<Integer>> floatEntries = floats.entries().iterator();
        assertSame(floatEntries.next(), floatEntries.next());

        IntSet intSet = new IntSet();
        intSet.add(1);
        assertReusableIterator(intSet);
        assertReusableIterator(new IntArray().add(1));
        assertReusableIterator(new LongArray().add(1L));
        assertReusableIterator(new FloatArray().add(1.0f));
    }

    @Test
    void objectQueueWrapsGrowsAndIteratesInQueueOrder() {
        ObjectQueue<Integer> queue = new ObjectQueue<Integer>(3);
        queue.addLast(0).addLast(1).addLast(2);
        assertEquals(0, queue.removeFirst());
        assertEquals(1, queue.removeFirst());
        queue.addLast(3).addLast(4).addFirst(-1);

        assertEquals(4, queue.size());
        assertEquals(-1, queue.first());
        assertEquals(2, queue.get(1));
        Array<Integer> values = new Array<Integer>();
        ObjectIterator<Integer> queueIterator = queue.iterator();
        while (queueIterator.hasNext()) {
            values.add(queueIterator.next());
        }
        assertArrayEquals(new Object[] { -1, 2, 3, 4 }, values.toArray());
        assertEquals(-1, queue.pollFirst());
        assertEquals(2, queue.pollFirst());
        assertEquals(3, queue.pollFirst());
        assertEquals(4, queue.pollFirst());
        assertNull(queue.pollFirst());
        assertThrows(NoSuchElementException.class, queue::removeFirst);
    }

    @Test
    void hashCollectionsKeepExactResultsAcrossLargeMutationCycles() {
        int count = 20_000;
        ObjectMap<Integer, Integer> objects = new ObjectMap<Integer, Integer>(count);
        IntMap<Integer> ints = new IntMap<Integer>(count);
        OrderedMap<Integer, Integer> ordered = new OrderedMap<Integer, Integer>(count);
        for (int i = 0; i < count; i++) {
            objects.put(i, i * 3);
            ints.put(i, i * 3);
            ordered.put(i, i * 3);
        }
        for (int i = 0; i < count; i += 2) {
            assertEquals(i * 3, objects.remove(i));
            assertEquals(i * 3, ints.remove(i));
            assertEquals(i * 3, ordered.remove(i));
        }
        for (int i = 0; i < count; i += 2) {
            objects.put(i, i * 5);
            ints.put(i, i * 5);
            ordered.put(i, i * 5);
        }

        long objectSum = 0L;
        int objectCount = 0;
        ObjectIterator<Integer> objectValues = objects.values().iterator();
        while (objectValues.hasNext()) {
            objectSum += objectValues.next();
            objectCount++;
        }
        long intSum = 0L;
        int intCount = 0;
        ObjectIterator<Integer> intValues = ints.values().iterator();
        while (intValues.hasNext()) {
            intSum += intValues.next();
            intCount++;
        }
        long orderedSum = 0L;
        int orderedCount = 0;
        ObjectIterator<Integer> orderedValues = ordered.values().iterator();
        while (orderedValues.hasNext()) {
            orderedSum += orderedValues.next();
            orderedCount++;
        }
        assertEquals(20_000, objectCount);
        assertEquals(20_000, intCount);
        assertEquals(20_000, orderedCount);
        assertEquals(objectSum, intSum);
        assertEquals(objectSum, orderedSum);
    }

    @Test
    void orderedMapPreservesInsertionOrderAcrossReplaceRemoveAndResize() {
        OrderedMap<Key, String> map = new OrderedMap<Key, String>(1);
        Key one = new Key(1);
        Key two = new Key(2);
        Key three = new Key(3);
        Key four = new Key(4);

        map.put(one, "one");
        map.put(two, null);
        map.put(three, "three");
        assertEquals("one", map.put(one, "uno"));
        assertNull(map.remove(two));
        map.put(four, "four");
        map.put(two, "two again");

        int index = 0;
        Key[] expectedKeys = { one, three, four, two };
        String[] expectedValues = { "uno", "three", "four", "two again" };
        ObjectIterator<OrderedMap.Entry<Key, String>> iterator = map.entries().iterator();
        while (iterator.hasNext()) {
            OrderedMap.Entry<Key, String> entry = iterator.next();
            assertSame(expectedKeys[index], entry.key());
            assertEquals(expectedValues[index], entry.value());
            index++;
        }

        assertEquals(expectedKeys.length, index);
        assertEquals("three", map.get(three));
        assertTrue(map.containsKey(two));
        assertEquals(one, map.findKey("uno"));
    }

    @Test
    void orderedMapCompactsTombstonesAndKeepsConstantTimeRemovalLinks() {
        int mask = 3;
        OrderedMap<Key, String> map = new OrderedMap<Key, String>(3, 0.75f);
        Key slot0 = new Key(10, intHashForSlot(0, mask));
        Key slot1 = new Key(11, intHashForSlot(1, mask));
        Key slot2 = new Key(12, intHashForSlot(2, mask));
        Key slot3 = new Key(13, intHashForSlot(3, mask));
        Key missing = new Key(14, intHashForSlot(0, mask));

        map.put(slot0, "zero");
        map.put(slot1, "one");
        map.put(slot2, "two");
        assertEquals("zero", map.remove(slot0));
        map.put(slot3, "three");

        assertEquals(3, map.size());
        assertEquals("missing", map.get(missing, "missing"));
        assertFalse(map.containsKey(missing));
        ObjectIterator<Key> keys = map.keys().iterator();
        assertSame(slot1, keys.next());
        assertSame(slot2, keys.next());
        assertSame(slot3, keys.next());
        assertThrows(NoSuchElementException.class, keys::next);

        ObjectMapView<Key, String> view = map.view();
        assertSame(view, map.view());
        map.clear();
        assertTrue(view.isEmpty());
    }

    @Test
    void mapsReserveAndShrinkWithoutLosingEntries() {
        ObjectMap<Key, String> objects = new ObjectMap<Key, String>(1);
        int objectInitialCapacity = objects.capacity();
        assertSame(objects, objects.ensureCapacity(64));
        assertTrue(objects.capacity() > objectInitialCapacity);
        Key objectKeepA = new Key(100, 100);
        Key objectKeepB = new Key(101, 101);
        objects.put(objectKeepA, "a");
        objects.put(objectKeepB, "b");
        Key[] objectRemoved = new Key[32];
        for (int i = 0; i < objectRemoved.length; i++) {
            objectRemoved[i] = new Key(i, i);
            objects.put(objectRemoved[i], "r" + i);
        }
        for (int i = 0; i < objectRemoved.length; i++) {
            objects.remove(objectRemoved[i]);
        }
        int objectLargeCapacity = objects.capacity();
        assertSame(objects, objects.shrink());
        assertTrue(objects.capacity() < objectLargeCapacity);
        assertEquals(2, objects.size());
        assertEquals("a", objects.get(objectKeepA));
        assertEquals("b", objects.get(objectKeepB));

        IntMap<String> ints = new IntMap<String>(1);
        int intInitialCapacity = ints.capacity();
        assertSame(ints, ints.ensureCapacity(64));
        assertTrue(ints.capacity() > intInitialCapacity);
        ints.put(100, "a");
        ints.put(101, "b");
        for (int i = 0; i < 32; i++) {
            ints.put(i, "r" + i);
        }
        for (int i = 0; i < 32; i++) {
            ints.remove(i);
        }
        int intLargeCapacity = ints.capacity();
        assertSame(ints, ints.shrink());
        assertTrue(ints.capacity() < intLargeCapacity);
        assertEquals(2, ints.size());
        assertEquals("a", ints.get(100));
        assertEquals("b", ints.get(101));

        LongMap<String> longs = new LongMap<String>(1);
        int longInitialCapacity = longs.capacity();
        assertSame(longs, longs.ensureCapacity(64));
        assertTrue(longs.capacity() > longInitialCapacity);
        longs.put(100L, "a");
        longs.put(101L, "b");
        for (long i = 0L; i < 32L; i++) {
            longs.put(i, "r" + i);
        }
        for (long i = 0L; i < 32L; i++) {
            longs.remove(i);
        }
        int longLargeCapacity = longs.capacity();
        assertSame(longs, longs.shrink());
        assertTrue(longs.capacity() < longLargeCapacity);
        assertEquals(2, longs.size());
        assertEquals("a", longs.get(100L));
        assertEquals("b", longs.get(101L));

        FloatMap<String> floats = new FloatMap<String>(1);
        int floatInitialCapacity = floats.capacity();
        assertSame(floats, floats.ensureCapacity(64));
        assertTrue(floats.capacity() > floatInitialCapacity);
        floats.put(100.5f, "a");
        floats.put(-0.0f, "b");
        for (int i = 0; i < 32; i++) {
            floats.put(i + 0.25f, "r" + i);
        }
        for (int i = 0; i < 32; i++) {
            floats.remove(i + 0.25f);
        }
        int floatLargeCapacity = floats.capacity();
        assertSame(floats, floats.shrink());
        assertTrue(floats.capacity() < floatLargeCapacity);
        assertEquals(2, floats.size());
        assertEquals("a", floats.get(100.5f));
        assertEquals("b", floats.get(-0.0f));
    }

    @Test
    void mapsRejectNegativeAdditionalCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectMap<Key, String>().ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> new IntMap<String>().ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> new LongMap<String>().ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> new FloatMap<String>().ensureCapacity(-1));
    }

    @Test
    void intMapHandlesZeroResizeAndRemoval() {
        IntMap<String> map = new IntMap<String>(1);
        map.put(0, "zero");
        for (int i = 1; i < 80; i++) {
            map.put(i, "v" + i);
        }

        assertEquals("zero", map.get(0));
        assertEquals("v79", map.get(79));
        assertEquals("v20", map.remove(20));
        assertFalse(map.containsKey(20));
        map.put(20, "again");
        assertEquals("again", map.get(20));
        assertEquals(80, map.size());
    }

    @Test
    void intMapEntriesIteratePrimitiveKeys() {
        IntMap<String> map = new IntMap<String>(2);
        map.put(0, "zero");
        map.put(3, "three");
        map.put(5, null);
        map.remove(3);

        int keySum = 0;
        int count = 0;
        boolean sawNullValue = false;
        ObjectIterator<IntMap.Entry<String>> iterator = map.entries().iterator();
        while (iterator.hasNext()) {
            IntMap.Entry<String> entry = iterator.next();
            keySum += entry.key();
            count++;
            if (entry.key() == 5) {
                sawNullValue = true;
                assertNull(entry.value());
            }
        }

        assertEquals(5, keySum);
        assertEquals(2, count);
        assertTrue(sawNullValue);
    }

    @Test
    void primitiveMapsFindValuesByEqualsIdentityAndNull() {
        String intValue = new String("int");
        String equalIntValue = new String("int");
        IntMap<String> ints = new IntMap<String>(2);
        ints.put(0, intValue);
        ints.put(7, null);

        assertTrue(ints.notEmpty());
        assertTrue(ints.containsValue(equalIntValue));
        assertFalse(ints.containsValue(equalIntValue, true));
        assertTrue(ints.containsValue(intValue, true));
        assertTrue(ints.containsValue(null));
        assertEquals(0, ints.findKey(equalIntValue, -1));
        assertEquals(-1, ints.findKey(equalIntValue, true, -1));
        assertEquals(0, ints.findKey(intValue, true, -1));
        assertEquals(7, ints.findKey(null, -1));
        ints.clear();
        assertFalse(ints.notEmpty());

        String longValue = new String("long");
        String equalLongValue = new String("long");
        LongMap<String> longs = new LongMap<String>(2);
        longs.put(0L, longValue);
        longs.put(9000L, null);

        assertTrue(longs.notEmpty());
        assertTrue(longs.containsValue(equalLongValue));
        assertFalse(longs.containsValue(equalLongValue, true));
        assertTrue(longs.containsValue(longValue, true));
        assertTrue(longs.containsValue(null));
        assertEquals(0L, longs.findKey(equalLongValue, -1L));
        assertEquals(-1L, longs.findKey(equalLongValue, true, -1L));
        assertEquals(0L, longs.findKey(longValue, true, -1L));
        assertEquals(9000L, longs.findKey(null, -1L));
        longs.clear();
        assertFalse(longs.notEmpty());

        String floatValue = new String("float");
        String equalFloatValue = new String("float");
        FloatMap<String> floats = new FloatMap<String>(2);
        floats.put(3.5f, floatValue);
        floats.put(-0.0f, null);

        assertTrue(floats.notEmpty());
        assertTrue(floats.containsValue(equalFloatValue));
        assertFalse(floats.containsValue(equalFloatValue, true));
        assertTrue(floats.containsValue(floatValue, true));
        assertTrue(floats.containsValue(null));
        assertEquals(Float.floatToIntBits(3.5f), Float.floatToIntBits(floats.findKey(equalFloatValue, -1.0f)));
        assertEquals(Float.floatToIntBits(-1.0f),
                Float.floatToIntBits(floats.findKey(equalFloatValue, true, -1.0f)));
        assertEquals(Float.floatToIntBits(3.5f),
                Float.floatToIntBits(floats.findKey(floatValue, true, -1.0f)));
        assertEquals(Float.floatToIntBits(-0.0f), Float.floatToIntBits(floats.findKey(null, 1.0f)));
        floats.clear();
        assertFalse(floats.notEmpty());
    }

    @Test
    void primitiveMapKeysAndValuesSkipRemovedSlots() {
        IntMap<String> ints = new IntMap<String>(2);
        ints.put(0, "zero");
        ints.put(7, null);
        ints.put(11, "eleven");
        ints.remove(7);

        int intKeySum = 0;
        int intKeyCount = 0;
        IntIterator intKeys = ints.keys().iterator();
        while (intKeys.hasNext()) {
            intKeySum += intKeys.nextInt();
            intKeyCount++;
        }
        int intValueCount = 0;
        boolean sawEleven = false;
        ObjectIterator<String> intValues = ints.values().iterator();
        while (intValues.hasNext()) {
            String value = intValues.next();
            intValueCount++;
            if ("eleven".equals(value)) {
                sawEleven = true;
            }
        }
        assertEquals(11, intKeySum);
        assertEquals(2, intKeyCount);
        assertEquals(2, intValueCount);
        assertTrue(sawEleven);
        assertThrows(NoSuchElementException.class, intKeys::nextInt);

        LongMap<String> longs = new LongMap<String>(2);
        longs.put(0L, "zero");
        longs.put(9000L, null);
        longs.put(17L, "seventeen");
        longs.remove(9000L);

        long longKeySum = 0L;
        int longKeyCount = 0;
        LongIterator longKeys = longs.keys().iterator();
        while (longKeys.hasNext()) {
            longKeySum += longKeys.nextLong();
            longKeyCount++;
        }
        int longValueCount = 0;
        boolean sawSeventeen = false;
        ObjectIterator<String> longValues = longs.values().iterator();
        while (longValues.hasNext()) {
            String value = longValues.next();
            longValueCount++;
            if ("seventeen".equals(value)) {
                sawSeventeen = true;
            }
        }
        assertEquals(17L, longKeySum);
        assertEquals(2, longKeyCount);
        assertEquals(2, longValueCount);
        assertTrue(sawSeventeen);
        assertThrows(NoSuchElementException.class, longKeys::nextLong);

        FloatMap<String> floats = new FloatMap<String>(2);
        floats.put(-0.0f, "negative zero");
        floats.put(1.5f, null);
        floats.put(2.5f, "two point five");
        floats.remove(1.5f);

        int floatKeyBits = 0;
        int floatKeyCount = 0;
        FloatIterator floatKeys = floats.keys().iterator();
        while (floatKeys.hasNext()) {
            floatKeyBits ^= Float.floatToIntBits(floatKeys.nextFloat());
            floatKeyCount++;
        }
        int floatValueCount = 0;
        boolean sawNegativeZero = false;
        boolean sawTwoPointFive = false;
        ObjectIterator<String> floatValues = floats.values().iterator();
        while (floatValues.hasNext()) {
            String value = floatValues.next();
            floatValueCount++;
            if ("negative zero".equals(value)) {
                sawNegativeZero = true;
            }
            if ("two point five".equals(value)) {
                sawTwoPointFive = true;
            }
        }
        assertEquals(Float.floatToIntBits(-0.0f) ^ Float.floatToIntBits(2.5f), floatKeyBits);
        assertEquals(2, floatKeyCount);
        assertEquals(2, floatValueCount);
        assertTrue(sawNegativeZero);
        assertTrue(sawTwoPointFive);
        assertThrows(NoSuchElementException.class, floatKeys::nextFloat);
    }

    @Test
    void intMapRehashesTombstonesBeforeProbeSentinelIsLost() {
        int mask = 3;
        IntMap<String> map = new IntMap<String>(3, 0.75f);
        int slot0 = intHashForSlot(0, mask);
        int slot1 = intHashForSlot(1, mask);
        int slot2 = intHashForSlot(2, mask);
        int slot3 = intHashForSlot(3, mask);
        int missing = nextDifferentIntHashForSlot(slot0, 0, mask);

        map.put(slot0, "zero");
        map.put(slot1, "one");
        map.put(slot2, "two");
        assertEquals("zero", map.remove(slot0));
        map.put(slot3, "three");

        assertEquals(3, map.size());
        assertEquals("missing", map.get(missing, "missing"));
        assertFalse(map.containsKey(missing));
    }

    @Test
    void longMapHandlesWideKeys() {
        LongMap<String> map = new LongMap<String>(2);
        long large = 0x7000_0000_0000_0001L;

        map.put(large, "large");
        map.put(-large, "negative");

        assertEquals("large", map.get(large));
        assertEquals("negative", map.remove(-large));
        assertNull(map.get(-large));
        assertEquals("fallback", map.get(-large, "fallback"));
    }

    @Test
    void longMapEntriesIterateWideKeys() {
        LongMap<String> map = new LongMap<String>(2);
        long large = 0x7000_0000_0000_0001L;
        map.put(large, "large");
        map.put(-large, "negative");
        map.remove(-large);

        int count = 0;
        long key = 0L;
        String value = null;
        ObjectIterator<LongMap.Entry<String>> iterator = map.entries().iterator();
        while (iterator.hasNext()) {
            LongMap.Entry<String> entry = iterator.next();
            count++;
            key = entry.key();
            value = entry.value();
        }

        assertEquals(1, count);
        assertEquals(large, key);
        assertEquals("large", value);
    }

    @Test
    void longMapRehashesTombstonesBeforeProbeSentinelIsLost() {
        int mask = 3;
        LongMap<String> map = new LongMap<String>(3, 0.75f);
        long slot0 = longHashForSlot(0, mask);
        long slot1 = longHashForSlot(1, mask);
        long slot2 = longHashForSlot(2, mask);
        long slot3 = longHashForSlot(3, mask);
        long missing = nextDifferentLongHashForSlot(slot0, 0, mask);

        map.put(slot0, "zero");
        map.put(slot1, "one");
        map.put(slot2, "two");
        assertEquals("zero", map.remove(slot0));
        map.put(slot3, "three");

        assertEquals(3, map.size());
        assertEquals("missing", map.get(missing, "missing"));
        assertFalse(map.containsKey(missing));
    }

    @Test
    void floatMapUsesFloatBitIdentity() {
        FloatMap<String> map = new FloatMap<String>(2);

        map.put(0.0f, "positive zero");
        map.put(-0.0f, "negative zero");
        map.put(Float.NaN, "nan");

        assertEquals("positive zero", map.get(0.0f));
        assertEquals("negative zero", map.get(-0.0f));
        assertEquals("nan", map.get(Float.intBitsToFloat(0x7fc00001)));
        assertTrue(map.containsKey(Float.NaN));
    }

    @Test
    void floatMapEntriesPreserveFloatBitIdentity() {
        FloatMap<String> map = new FloatMap<String>(2);
        float customNaN = Float.intBitsToFloat(0x7fc00001);
        map.put(0.0f, "positive zero");
        map.put(-0.0f, "negative zero");
        map.put(customNaN, "nan");

        boolean sawPositiveZero = false;
        boolean sawNegativeZero = false;
        boolean sawNan = false;
        ObjectIterator<FloatMap.Entry<String>> iterator = map.entries().iterator();
        while (iterator.hasNext()) {
            FloatMap.Entry<String> entry = iterator.next();
            int keyBits = Float.floatToIntBits(entry.key());
            if (keyBits == Float.floatToIntBits(0.0f)) {
                sawPositiveZero = true;
                assertEquals("positive zero", entry.value());
            }
            if (keyBits == Float.floatToIntBits(-0.0f)) {
                sawNegativeZero = true;
                assertEquals("negative zero", entry.value());
            }
            if (keyBits == Float.floatToIntBits(customNaN)) {
                sawNan = true;
                assertEquals("nan", entry.value());
            }
        }

        assertTrue(sawPositiveZero);
        assertTrue(sawNegativeZero);
        assertTrue(sawNan);
    }

    @Test
    void floatMapRehashesTombstonesBeforeProbeSentinelIsLost() {
        int mask = 3;
        FloatMap<String> map = new FloatMap<String>(3, 0.75f);
        float slot0 = floatHashForSlot(0, mask);
        float slot1 = floatHashForSlot(1, mask);
        float slot2 = floatHashForSlot(2, mask);
        float slot3 = floatHashForSlot(3, mask);
        float missing = nextDifferentFloatHashForSlot(slot0, 0, mask);

        map.put(slot0, "zero");
        map.put(slot1, "one");
        map.put(slot2, "two");
        assertEquals("zero", map.remove(slot0));
        map.put(slot3, "three");

        assertEquals(3, map.size());
        assertEquals("missing", map.get(missing, "missing"));
        assertFalse(map.containsKey(missing));
    }

    @Test
    void linkedListAddsRemovesAndIterates() {
        ObjectLinkedList<String> list = new ObjectLinkedList<String>();
        ObjectLinkedList.Node<String> middle = list.addLast("b");
        list.addFirst("a");
        list.addLast("c");

        assertEquals("a", list.first());
        assertEquals("c", list.last());
        assertTrue(list.notEmpty());
        assertEquals("b", list.remove(middle));
        assertEquals(2, list.size());

        ObjectIterator<String> iterator = list.iterator();
        assertEquals("a", iterator.next());
        assertEquals("c", iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals("a", list.removeFirst());
        assertEquals("c", list.removeLast());
        assertTrue(list.isEmpty());
        assertFalse(list.notEmpty());
    }

    @Test
    void linkedListRejectsForeignNodes() {
        ObjectLinkedList<String> first = new ObjectLinkedList<String>();
        ObjectLinkedList<String> second = new ObjectLinkedList<String>();
        ObjectLinkedList.Node<String> node = first.addLast("value");

        assertThrows(IllegalArgumentException.class, () -> second.remove(node));
        first.clear();
        assertThrows(IllegalArgumentException.class, () -> first.remove(node));
    }

    @Test
    void linkedListPoolsRemovedNodesAndClearsTheirValues() {
        ObjectLinkedList<Object> list = new ObjectLinkedList<Object>(1);
        Object firstValue = new Object();
        ObjectLinkedList.Node<Object> firstNode = list.addLast(firstValue);

        assertEquals(1, list.capacity());
        assertSame(firstValue, list.remove(firstNode));
        assertNull(firstNode.value());

        Object secondValue = new Object();
        ObjectLinkedList.Node<Object> reusedNode = list.addFirst(secondValue);

        assertSame(firstNode, reusedNode);
        assertSame(secondValue, reusedNode.value());
        assertEquals(1, list.capacity());

        list.clear();
        assertNull(reusedNode.value());
        assertEquals(1, list.capacity());
    }

    @Test
    void linkedListCanReserveNodesBeforeAdds() {
        ObjectLinkedList<String> list = new ObjectLinkedList<String>(0);

        list.ensureCapacity(3);

        assertEquals(3, list.capacity());
        list.addLast("a");
        list.addLast("b");
        list.addLast("c");
        assertEquals(3, list.capacity());
        assertThrows(IllegalArgumentException.class, () -> list.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> new ObjectLinkedList<String>(-1));
    }

    private static void assertReusableIterator(ObjectIterable<?> iterable) {
        ObjectIterator<?> iterator = iterable.iterator();
        assertSame(iterator, iterable.iterator());
    }

    private static void assertReusableIterator(IntIterable iterable) {
        IntIterator iterator = iterable.iterator();
        assertSame(iterator, iterable.iterator());
    }

    private static void assertReusableIterator(LongIterable iterable) {
        LongIterator iterator = iterable.iterator();
        assertSame(iterator, iterable.iterator());
    }

    private static void assertReusableIterator(FloatIterable iterable) {
        FloatIterator iterator = iterable.iterator();
        assertSame(iterator, iterable.iterator());
    }

    private static final class Key {
        private final int value;
        private final int hash;

        Key(int value) {
            this(value, value & 1);
        }

        Key(int value, int hash) {
            this.value = value;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && ((Key)other).value == value;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static int intHashForSlot(int slot, int mask) {
        for (int value = 0; ; value++) {
            if ((CollectionHash.mix(value) & mask) == slot) {
                return value;
            }
        }
    }

    private static int nextDifferentIntHashForSlot(int first, int slot, int mask) {
        for (int value = first + 1; ; value++) {
            if ((CollectionHash.mix(value) & mask) == slot) {
                return value;
            }
        }
    }

    private static long longHashForSlot(int slot, int mask) {
        for (long value = 0L; ; value++) {
            if ((CollectionHash.mix(value) & mask) == slot) {
                return value;
            }
        }
    }

    private static long nextDifferentLongHashForSlot(long first, int slot, int mask) {
        for (long value = first + 1L; ; value++) {
            if ((CollectionHash.mix(value) & mask) == slot) {
                return value;
            }
        }
    }

    private static float floatHashForSlot(int slot, int mask) {
        for (int bits = 1; ; bits++) {
            float value = Float.intBitsToFloat(bits);
            if (!Float.isNaN(value) && (CollectionHash.mix(Float.floatToIntBits(value)) & mask) == slot) {
                return value;
            }
        }
    }

    private static float nextDifferentFloatHashForSlot(float first, int slot, int mask) {
        int firstBits = Float.floatToIntBits(first);
        for (int bits = firstBits + 1; ; bits++) {
            float value = Float.intBitsToFloat(bits);
            if (!Float.isNaN(value) && (CollectionHash.mix(Float.floatToIntBits(value)) & mask) == slot) {
                return value;
            }
        }
    }
}
