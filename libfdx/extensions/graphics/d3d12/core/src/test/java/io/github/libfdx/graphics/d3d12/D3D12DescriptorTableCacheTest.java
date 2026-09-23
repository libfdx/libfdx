package io.github.libfdx.graphics.d3d12;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D3D12DescriptorTableCacheTest {
    @Test
    void samplerChangesAndTableLengthsDoNotAlias() {
        var cache = new D3D12DescriptorTableCache(16, 16);
        long[] textures = {10, 20};
        long[] samplers = {30, 40};
        int original = cache.acquire(textures, 2, samplers, 2);
        samplers[0] = 31;
        int differentSampler = cache.acquire(textures, 2, samplers, 2);
        assertNotEquals(original, differentSampler);
        int shorter = cache.acquire(textures, 1, samplers, 1);
        assertNotEquals(differentSampler, shorter);
        assertEquals(4, cache.textureStart(shorter));
        samplers[0] = 30;
        assertEquals(original, cache.acquire(textures, 2, samplers, 2));
    }

    @Test
    void repeatedDrawsReuseSnapshotsAndRewritesAllocateDistinctTables() {
        var cache = new D3D12DescriptorTableCache(8, 4);
        long[] textures = {10, 20};
        long[] samplers = {30, 40};
        int first = cache.acquire(textures, 2, samplers, 2);
        textures[0] = 11;
        int rewritten = cache.acquire(textures, 2, samplers, 2);
        assertNotEquals(first, rewritten);
        assertEquals(2, cache.textureStart(rewritten));
        for (int i = 0; i < 10_000; i++) {
            textures[0] = (i & 1) == 0 ? 10 : 11;
            assertEquals((i & 1) == 0 ? first : rewritten, cache.acquire(textures, 2, samplers, 2));
            assertFalse(cache.inserted());
        }
        textures[0] = 12;
        assertThrows(RuntimeException.class, () -> cache.acquire(textures, 2, samplers, 2));
        cache.clear();
        assertEquals(0, cache.acquire(textures, 2, samplers, 2));
        assertTrue(cache.inserted());
        assertEquals(0, cache.textureStart(0));
    }
}
