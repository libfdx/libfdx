package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import java.util.Arrays;

/** Immutable descriptor snapshots reused until the owning frame's fence completes. */
final class D3D12DescriptorTableCache {
    private final long[] textures;
    private final long[] samplers;
    private final int[] textureStarts;
    private final int[] samplerStarts;
    private final int[] textureCounts;
    private final int[] samplerCounts;
    private final int[] buckets;
    private int size;
    private int textureCursor;
    private int samplerCursor;
    private boolean inserted;

    D3D12DescriptorTableCache(int textureCapacity, int samplerCapacity) {
        textures = new long[textureCapacity];
        samplers = new long[samplerCapacity];
        textureStarts = new int[textureCapacity];
        samplerStarts = new int[textureCapacity];
        textureCounts = new int[textureCapacity];
        samplerCounts = new int[textureCapacity];
        buckets = new int[Integer.highestOneBit(textureCapacity) << 2];
    }

    void clear() {
        Arrays.fill(buckets, 0);
        size = textureCursor = samplerCursor = 0;
        inserted = false;
    }

    int acquire(long[] textureHandles, int textureCount, long[] samplerHandles, int samplerCount) {
        int hash = 31 * textureCount + samplerCount;
        for (int i = 0; i < textureCount; i++) hash = 31 * hash + Long.hashCode(textureHandles[i]);
        for (int i = 0; i < samplerCount; i++) hash = 31 * hash + Long.hashCode(samplerHandles[i]);
        int bucket = (hash ^ (hash >>> 16)) & (buckets.length - 1);
        while (buckets[bucket] != 0) {
            int entry = buckets[bucket] - 1;
            if (textureCounts[entry] == textureCount && samplerCounts[entry] == samplerCount
                    && Arrays.equals(textures, textureStarts[entry], textureStarts[entry] + textureCount,
                            textureHandles, 0, textureCount)
                    && Arrays.equals(samplers, samplerStarts[entry], samplerStarts[entry] + samplerCount,
                            samplerHandles, 0, samplerCount)) {
                inserted = false;
                return entry;
            }
            bucket = (bucket + 1) & (buckets.length - 1);
        }
        if (textureCursor + textureCount > textures.length || samplerCursor + samplerCount > samplers.length
                || size == textureStarts.length) {
            throw new FdxException("Direct3D 12 frame descriptor heap is exhausted by distinct binding tables");
        }
        int entry = size++;
        textureStarts[entry] = textureCursor;
        samplerStarts[entry] = samplerCursor;
        textureCounts[entry] = textureCount;
        samplerCounts[entry] = samplerCount;
        System.arraycopy(textureHandles, 0, textures, textureCursor, textureCount);
        System.arraycopy(samplerHandles, 0, samplers, samplerCursor, samplerCount);
        textureCursor += textureCount;
        samplerCursor += samplerCount;
        buckets[bucket] = entry + 1;
        inserted = true;
        return entry;
    }

    boolean inserted() { return inserted; }
    int textureStart(int entry) { return textureStarts[entry]; }
    int samplerStart(int entry) { return samplerStarts[entry]; }
}
