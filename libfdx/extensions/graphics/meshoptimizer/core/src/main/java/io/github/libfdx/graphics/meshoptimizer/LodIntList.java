package io.github.libfdx.graphics.meshoptimizer;

import java.util.Arrays;

/** Primitive adjacency storage, allocated during preparation only. */
final class LodIntList {
    int[] data = new int[8];
    int size;
    void add(int value) {
        if (size == data.length) data = Arrays.copyOf(data, size * 2);
        data[size++] = value;
    }
}
