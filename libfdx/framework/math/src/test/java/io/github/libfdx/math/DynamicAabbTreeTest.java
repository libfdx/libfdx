package io.github.libfdx.math;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DynamicAabbTreeTest {
    private final DynamicAabbTree tree = new DynamicAabbTree();
    private final IntArray hits = new IntArray();

    @Test
    void copiesBoundsAndReplacesResultsAcrossMovesRemovalsAndClear() {
        BoundingBox bounds = box(-2, -3, 0, 1, 2, 0);
        tree.put(-1, bounds);
        tree.put(0, box(1, 2, 0, 5, 6, 0));
        bounds.min().set(20, 20, 0);
        bounds.max().set(21, 21, 0);
        BoundingBox corner = box(1, 2, 0, 1, 2, 0);
        hits.add(12345);
        tree.query(corner, hits);
        assertHits(-1, 0);
        tree.put(-1, bounds);
        assertEquals(2, tree.size());
        tree.query(corner, hits);
        assertHits(0);
        assertTrue(tree.remove(0));
        assertFalse(tree.remove(0));
        tree.query(bounds, hits);
        assertHits(-1);
        tree.clear();
        tree.query(bounds, hits);
        assertHits();
        tree.put(Integer.MIN_VALUE, corner);
        tree.query(corner, hits);
        assertHits(Integer.MIN_VALUE);
    }

    @Test
    void clipsSegmentsToBothEndpointsOnAllAxes() {
        tree.put(1, box(0, 0, 0, 2, 2, 2));
        tree.put(2, box(1, 1, 3, 1, 1, 3));
        tree.put(3, box(0, 0, 4, 2, 2, 5));
        tree.querySegment(new Vector3(1, 1, 3), new Vector3(1, 1, 0), hits);
        assertHits(1, 2);
        tree.querySegment(new Vector3(-1, 1, 1), new Vector3(0, 2, 2), hits);
        assertHits(1);
        tree.querySegment(new Vector3(2, 2, 2), new Vector3(2, 2, 2), hits);
        assertHits(1);
        tree.querySegment(new Vector3(3, 0, 0), new Vector3(3, 2, 2), hits);
        assertHits();
        tree.querySegment(new Vector3(0, 0, 4), new Vector3(2, 2, 5), hits);
        assertHits(3);
        tree.querySegment(new Vector3(-Float.MAX_VALUE, 1, 1), new Vector3(Float.MAX_VALUE, 1, 1), hits);
        assertHits(1);
    }

    @Test
    void rejectsInvalidInputsBeforeChangingTreeOrOutput() {
        BoundingBox point = box(0, 0, 0, 0, 0, 0);
        tree.put(7, point);
        assertThrows(FdxException.class, () -> tree.put(7, box(1, 0, 0, 0, 0, 0)));
        assertThrows(FdxException.class, () -> tree.put(8, box(0, 0, 0, 1, Float.POSITIVE_INFINITY, 1)));
        assertThrows(FdxException.class, () -> tree.put(8, null));
        tree.query(point, hits);
        assertHits(7);
        assertThrows(FdxException.class, () -> tree.querySegment(new Vector3(Float.NaN, 0, 0), Vector3.ZERO, hits));
        assertThrows(FdxException.class, () -> tree.query(point, null));
        assertThrows(FdxException.class, () -> tree.query(null, hits));
        assertHits(7);
        assertEquals(1, tree.size());
    }

    @Test
    void mixedUpdatesAgreeWithAnExhaustiveScan() {
        BoundingBox[] reference = new BoundingBox[160];
        Random random = new Random(0x5afd_0071);
        for (int step = 0; step < 3500; step++) {
            int id = random.nextInt(reference.length);
            if (random.nextInt(4) == 0) {
                assertEquals(reference[id] != null, tree.remove(id));
                reference[id] = null;
            } else {
                reference[id] = randomBox(random);
                tree.put(id, reference[id]);
            }
            BoundingBox region = randomBox(random);
            Set<Integer> expected = new HashSet<>();
            for (int i = 0; i < reference.length; i++) {
                if (reference[i] != null && overlaps(reference[i], region)) expected.add(i);
            }
            tree.query(region, hits);
            for (int i = 0; i < hits.size(); i++) {
                assertTrue(expected.remove(hits.get(i)), "Unexpected or duplicate ID at step " + step);
            }
            assertTrue(expected.isEmpty(), "Missing hits at step " + step);
        }
    }

    @Test
    void randomizedSegmentsAgreeWithFaceIntersections() {
        Random random = new Random(0x77a3);
        BoundingBox[] boxes = new BoundingBox[128];
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = randomBox(random);
            tree.put(i, boxes[i]);
        }
        for (int step = 0; step < 300; step++) {
            Vector3 a = new Vector3(random.nextFloat() * 60 - 30, random.nextFloat() * 60 - 30,
                    random.nextFloat() * 14 - 7);
            Vector3 b = new Vector3(random.nextFloat() * 60 - 30, random.nextFloat() * 60 - 30,
                    random.nextFloat() * 14 - 7);
            tree.querySegment(a, b, hits);
            int matches = 0;
            for (int i = 0; i < boxes.length; i++) {
                boolean expected = crossesBox(a, b, boxes[i]);
                assertEquals(expected, hits.contains(i));
                if (expected) matches++;
            }
            assertEquals(matches, hits.size());
        }
    }

    // Independent oracle: test the starting point and intersections with each of the six faces.
    private static boolean crossesBox(Vector3 a, Vector3 b, BoundingBox box) {
        double[] start = {a.x(), a.y(), a.z()};
        double[] end = {b.x(), b.y(), b.z()};
        double[] lo = {box.min().x(), box.min().y(), box.min().z()};
        double[] hi = {box.max().x(), box.max().y(), box.max().z()};
        if (start[0] >= lo[0] && start[0] <= hi[0] && start[1] >= lo[1] && start[1] <= hi[1]
                && start[2] >= lo[2] && start[2] <= hi[2]) {
            return true;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (start[axis] == end[axis]) continue;
            for (double face : new double[] {lo[axis], hi[axis]}) {
                double t = (face - start[axis]) / (end[axis] - start[axis]);
                if (t < 0 || t > 1) continue;
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                double x = start[u] + t * (end[u] - start[u]);
                double y = start[v] + t * (end[v] - start[v]);
                if (x >= lo[u] && x <= hi[u] && y >= lo[v] && y <= hi[v]) return true;
            }
        }
        return false;
    }

    @Test
    void handlesASortedCoplanarWorldAndRepeatedReinsertion() {
        int count = 8192;
        for (int id = 0; id < count; id++) tree.put(id, box(id, 0, 0, id + 1, 1, 0));
        for (int id = 0; id < count; id += 2) assertTrue(tree.remove(id));
        for (int id = count - 2; id >= 0; id -= 2) tree.put(id, box(-id, 2, 0, 1 - id, 3, 0));
        tree.query(box(-count, 2, 0, 1, 3, 0), hits);
        assertEquals(count / 2, hits.size());
        tree.querySegment(new Vector3(110, 0, 0), new Vector3(110, 1, 0), hits);
        assertHits(109);
    }

    private void assertHits(int... ids) {
        int[] actual = hits.toArray();
        Arrays.sort(actual);
        assertArrayEquals(ids, actual);
    }

    private static BoundingBox randomBox(Random random) {
        int x = random.nextInt(50) - 25;
        int y = random.nextInt(50) - 25;
        int z = random.nextInt(12) - 6;
        return box(x, y, z, x + random.nextInt(16), y + random.nextInt(16), z + random.nextInt(5));
    }

    private static BoundingBox box(float x, float y, float z, float maxX, float maxY, float maxZ) {
        return BoundingBox.of(new Vector3(x, y, z), new Vector3(maxX, maxY, maxZ));
    }

    private static boolean overlaps(BoundingBox a, BoundingBox b) {
        return a.min().x() <= b.max().x() && a.max().x() >= b.min().x()
                && a.min().y() <= b.max().y() && a.max().y() >= b.min().y()
                && a.min().z() <= b.max().z() && a.max().z() >= b.min().z();
    }

}
