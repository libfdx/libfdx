package io.github.libfdx.graphics.g3d;

/** Bounded, allocation-free O(n log n) descending depth ordering. */
final class ParticleDepthSort3D {
    private ParticleDepthSort3D() {}

    static void sort(float[] depth, int[] order, int count) {
        for (int i = 0; i < count; i++) order[i] = i;
        for (int i = count / 2 - 1; i >= 0; i--) sift(depth, order, i, count);
        for (int end = count - 1; end > 0; end--) {
            int first = order[0];
            order[0] = order[end];
            order[end] = first;
            sift(depth, order, 0, end);
        }
    }

    private static void sift(float[] depth, int[] order, int root, int count) {
        while (root < count / 2) {
            int child = root * 2 + 1;
            if (child + 1 < count && before(depth, order[child + 1], order[child])) child++;
            if (!before(depth, order[child], order[root])) return;
            int value = order[root];
            order[root] = order[child];
            order[child] = value;
            root = child;
        }
    }

    private static boolean before(float[] depth, int a, int b) {
        return depth[a] < depth[b] || (depth[a] == depth[b] && a > b);
    }
}
