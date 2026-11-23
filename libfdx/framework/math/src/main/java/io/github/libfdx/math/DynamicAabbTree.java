package io.github.libfdx.math;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;

/**
 * Indexes world-space axis-aligned bounding boxes by an application-defined integer ID.
 * Use it to find candidates for collision, picking, or proximity checks. A 2D game can
 * use the same Z coordinate for all bounds and queries. Touching counts as intersection.
 *
 * <p>The tree copies each box on insertion or update. Queries replace the contents of
 * a caller-owned array with matching IDs in unspecified order. Once that array has
 * sufficient capacity, queries and updates to existing IDs allocate no objects.
 * Tree instances are application-owned and confined to one thread.</p>
 *
 * <pre>{@code
 * DynamicAabbTree tree = new DynamicAabbTree();
 * IntArray nearby = new IntArray();
 * tree.put(enemyId, enemyBounds);
 * tree.query(playerBounds, nearby);
 * for (int i = 0; i < nearby.size(); i++) {
 *     checkPlayerContact(nearby.get(i));
 * }
 * }</pre>
 */
public final class DynamicAabbTree {
    private final IntMap<Node> leaves = new IntMap<>();
    private Node root;
    private Node free;

    /** @return the number of distinct indexed IDs */
    public int size() {
        return leaves.size();
    }

    /**
     * Inserts or moves an ID. Bounds must be finite and ordered on all three axes.
     * @param id any signed game object ID
     * @param bounds world-space box to copy, including its boundary
     */
    public void put(int id, BoundingBox bounds) {
        checkBounds(bounds);
        Vector3 min = bounds.min();
        Vector3 max = bounds.max();
        Node leaf = leaves.get(id);
        if (leaf == null) {
            leaf = acquire();
            leaf.id = id;
            leaves.put(id, leaf);
        } else {
            if (leaf.minX == min.x() && leaf.minY == min.y() && leaf.minZ == min.z()
                    && leaf.maxX == max.x() && leaf.maxY == max.y() && leaf.maxZ == max.z()) {
                return;
            }
            detach(leaf);
        }
        leaf.minX = min.x();
        leaf.minY = min.y();
        leaf.minZ = min.z();
        leaf.maxX = max.x();
        leaf.maxY = max.y();
        leaf.maxZ = max.z();
        insert(leaf);
    }

    /**
     * @param id the indexed ID to remove
     * @return true if the tree contained the ID
     */
    public boolean remove(int id) {
        Node leaf = leaves.remove(id);
        if (leaf == null) {
            return false;
        }
        detach(leaf);
        recycle(leaf);
        return true;
    }

    /** Removes all entries and releases references to tree nodes, including pooled nodes. */
    public void clear() {
        leaves.clear();
        root = null;
        free = null;
    }

    /**
     * Finds all boxes intersecting a closed query box.
     * @param bounds finite, ordered search bounds in world coordinates
     * @param results caller-owned array to clear and fill
     */
    public void query(BoundingBox bounds, IntArray results) {
        checkBounds(bounds);
        resetResults(results);
        if (root != null) {
            query(root, bounds.min(), bounds.max(), results);
        }
    }

    private static void query(Node node, Vector3 min, Vector3 max, IntArray results) {
        if (node.maxX < min.x() || node.minX > max.x()
                || node.maxY < min.y() || node.minY > max.y()
                || node.maxZ < min.z() || node.minZ > max.z()) {
            return;
        }
        if (node.left == null) {
            results.add(node.id);
        } else {
            query(node.left, min, max, results);
            query(node.right, min, max, results);
        }
    }

    /**
     * Replaces results with all IDs whose boxes intersect the finite segment, including
     * either endpoint. Equal endpoints perform a point query. Results are unordered.
     * @param from finite starting point
     * @param to finite ending point
     * @param results caller-owned array to clear and fill
     */
    public void querySegment(Vector3 from, Vector3 to, IntArray results) {
        if (!finite(from) || !finite(to)) {
            throw new FdxException("Segment endpoints must be finite");
        }
        resetResults(results);
        if (root != null) {
            querySegment(root, from, (double) to.x() - from.x(), (double) to.y() - from.y(),
                    (double) to.z() - from.z(), results);
        }
    }

    private static void querySegment(Node node, Vector3 from, double dx, double dy, double dz, IntArray results) {
        double enter = 0;
        double exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double direction, origin, min, max;
            switch (axis) {
                case 0 -> {
                    direction = dx;
                    origin = from.x();
                    min = node.minX;
                    max = node.maxX;
                }
                case 1 -> {
                    direction = dy;
                    origin = from.y();
                    min = node.minY;
                    max = node.maxY;
                }
                default -> {
                    direction = dz;
                    origin = from.z();
                    min = node.minZ;
                    max = node.maxZ;
                }
            }
            if (direction == 0) {
                if (origin < min || origin > max) {
                    return;
                }
            } else {
                double t1 = (min - origin) / direction;
                double t2 = (max - origin) / direction;
                enter = Math.max(enter, Math.min(t1, t2));
                exit = Math.min(exit, Math.max(t1, t2));
                if (enter > exit) {
                    return;
                }
            }
        }
        if (node.left == null) {
            results.add(node.id);
        } else {
            querySegment(node.left, from, dx, dy, dz, results);
            querySegment(node.right, from, dx, dy, dz, results);
        }
    }

    private static void checkBounds(BoundingBox bounds) {
        if (bounds == null || !finite(bounds.min()) || !finite(bounds.max())
                || bounds.min().x() > bounds.max().x() || bounds.min().y() > bounds.max().y()
                || bounds.min().z() > bounds.max().z()) {
            throw new FdxException("Bounds must have finite, ordered extrema");
        }
    }

    private static boolean finite(Vector3 vector) {
        return vector != null && Float.isFinite(vector.x())
                && Float.isFinite(vector.y()) && Float.isFinite(vector.z());
    }

    private static void resetResults(IntArray results) {
        if (results == null) {
            throw new FdxException("Results cannot be null");
        }
        results.clear();
    }

    private Node acquire() {
        if (free == null) {
            return new Node();
        }
        Node node = free;
        free = node.parent;
        node.parent = null;
        return node;
    }

    private void recycle(Node node) {
        node.left = null;
        node.right = null;
        node.height = 0;
        node.parent = free;
        free = node;
    }

    private void insert(Node leaf) {
        if (root == null) {
            root = leaf;
            return;
        }
        Node sibling = root;
        while (sibling.left != null) {
            double leftCost = sibling.left.enlargement(leaf);
            double rightCost = sibling.right.enlargement(leaf);
            boolean chooseLeft = leftCost < rightCost
                    || (leftCost == rightCost && sibling.left.height <= sibling.right.height);
            sibling = chooseLeft ? sibling.left : sibling.right;
        }
        Node branch = acquire();
        replace(sibling, branch);
        branch.left = sibling;
        branch.right = leaf;
        sibling.parent = branch;
        leaf.parent = branch;
        refit(branch);
    }

    private void detach(Node leaf) {
        Node branch = leaf.parent;
        if (branch == null) {
            root = null;
            return;
        }
        Node sibling = branch.left == leaf ? branch.right : branch.left;
        replace(branch, sibling);
        leaf.parent = null;
        recycle(branch);
        refit(sibling.parent);
    }

    private void replace(Node previous, Node replacement) {
        Node parent = previous.parent;
        replacement.parent = parent;
        if (parent == null) {
            root = replacement;
        } else if (parent.left == previous) {
            parent.left = replacement;
        } else {
            parent.right = replacement;
        }
    }

    private void refit(Node branch) {
        while (branch != null) {
            int balance = branch.left.height - branch.right.height;
            if (balance > 1 || balance < -1) {
                branch = rotate(branch, balance > 1);
            }
            branch.updateBounds();
            branch = branch.parent;
        }
    }

    private Node rotate(Node branch, boolean promoteLeft) {
        Node promoted = promoteLeft ? branch.left : branch.right;
        Node other = promoteLeft ? branch.right : branch.left;
        // Keep the taller grandchild at the top and pair the shorter one with the opposite subtree.
        boolean keepLeft = promoted.left.height >= promoted.right.height;
        Node keep = keepLeft ? promoted.left : promoted.right;
        Node move = keepLeft ? promoted.right : promoted.left;
        replace(branch, promoted);
        promoted.left = branch;
        promoted.right = keep;
        branch.parent = promoted;
        branch.left = other;
        branch.right = move;
        move.parent = branch;
        branch.updateBounds();
        return promoted;
    }

    private static final class Node {
        Node parent, left, right;
        int id, height;
        float minX, minY, minZ, maxX, maxY, maxZ;

        void updateBounds() {
            minX = Math.min(left.minX, right.minX);
            minY = Math.min(left.minY, right.minY);
            minZ = Math.min(left.minZ, right.minZ);
            maxX = Math.max(left.maxX, right.maxX);
            maxY = Math.max(left.maxY, right.maxY);
            maxZ = Math.max(left.maxZ, right.maxZ);
            height = 1 + Math.max(left.height, right.height);
        }

        double enlargement(Node box) {
            double x = (double) maxX - minX;
            double y = (double) maxY - minY;
            double z = (double) maxZ - minZ;
            double unionX = (double) Math.max(maxX, box.maxX) - Math.min(minX, box.minX);
            double unionY = (double) Math.max(maxY, box.maxY) - Math.min(minY, box.minY);
            double unionZ = (double) Math.max(maxZ, box.maxZ) - Math.min(minZ, box.minZ);
            return (unionX * unionY + unionX * unionZ + unionY * unionZ) - (x * y + x * z + y * z);
        }

    }

}
