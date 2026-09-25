package io.github.libfdx.graphics.meshoptimizer;

import java.util.Arrays;

/** Primitive indexed-candidate heap; lazy revision invalidation avoids per-collapse objects. */
final class LodCollapseHeap {
    int[] from = new int[256], to = new int[256], revision = new int[256];
    double[] error = new double[256];
    int size;
    void add(int a, int b, int version, double cost) {
        if (!Double.isFinite(cost)) return;
        if (size == from.length) {
            int capacity = Math.multiplyExact(size, 2);
            from = Arrays.copyOf(from, capacity); to = Arrays.copyOf(to, capacity);
            revision = Arrays.copyOf(revision, capacity); error = Arrays.copyOf(error, capacity);
        }
        int i = size++;
        while (i > 0) {
            int p = (i - 1) >>> 1;
            if (!less(cost, a, b, error[p], from[p], to[p])) break;
            copy(p, i); i = p;
        }
        from[i] = a; to[i] = b; revision[i] = version; error[i] = cost;
    }
    void pop() {
        if (--size == 0) return;
        int a = from[size], b = to[size], v = revision[size]; double cost = error[size];
        int i = 0;
        while (i * 2 + 1 < size) {
            int c = i * 2 + 1;
            if (c + 1 < size && less(error[c + 1], from[c + 1], to[c + 1], error[c], from[c], to[c])) c++;
            if (!less(error[c], from[c], to[c], cost, a, b)) break;
            copy(c, i); i = c;
        }
        from[i] = a; to[i] = b; revision[i] = v; error[i] = cost;
    }
    private void copy(int a, int b) { from[b] = from[a]; to[b] = to[a]; revision[b] = revision[a]; error[b] = error[a]; }
    private static boolean less(double x, int a, int b, double y, int c, int d) {
        return x < y || x == y && (a < c || a == c && b < d);
    }
}
