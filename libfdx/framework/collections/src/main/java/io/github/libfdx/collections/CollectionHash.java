package io.github.libfdx.collections;

final class CollectionHash {
    static final byte EMPTY = 0;
    static final byte USED = 1;
    static final byte REMOVED = 2;

    private CollectionHash() {
    }

    static int tableSize(int capacity, float loadFactor) {
        int requested = Math.max(2, (int)Math.ceil(capacity / loadFactor));
        int tableSize = 1;
        while (tableSize < requested) {
            tableSize <<= 1;
        }
        return tableSize;
    }

    static int threshold(int tableSize, float loadFactor) {
        return Math.max(1, (int)(tableSize * loadFactor));
    }

    static void checkLoadFactor(float loadFactor) {
        if (loadFactor <= 0.0f || loadFactor >= 1.0f || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("loadFactor must be > 0 and < 1");
        }
    }

    static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return value;
    }

    static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int)(value ^ (value >>> 32));
    }
}
