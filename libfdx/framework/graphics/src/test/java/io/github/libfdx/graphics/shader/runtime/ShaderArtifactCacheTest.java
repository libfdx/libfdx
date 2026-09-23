package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShaderArtifactCacheTest {
    private static final ShaderCacheKey KEY = ShaderCacheKey.of(ShaderCacheLayer.DXIL, "compiler-v1", "hlsl", "vs_6_0", "-O3");

    @Test
    void exactInputsOptionsVersionsAndLayersHaveIndependentIdentities() {
        assertNotEquals(KEY, ShaderCacheKey.of(ShaderCacheLayer.DXIL, "compiler-v2", "hlsl", "vs_6_0", "-O3"));
        assertNotEquals(KEY, ShaderCacheKey.of(ShaderCacheLayer.DXIL, "compiler-v1", "hlsl", "vs_6_0", "-Od"));
        assertNotEquals(KEY.digest(), ShaderCacheKey.of(ShaderCacheLayer.SPIRV, "compiler-v1", "hlsl", "vs_6_0", "-O3").digest());
        assertNotEquals(ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "ab", "c"),
                ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "a", "bc"));
        assertThrows(IllegalArgumentException.class, () -> new ShaderCacheKey(ShaderCacheLayer.DXIL, "../escape"));
    }

    @Test
    void writeCompletionAndReadCompletionRemainAsynchronous() {
        MemoryStore store = new MemoryStore();
        store.pendingWrite = FdxFuture.pending();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        byte[] source = {1, 2, 3};
        var written = cache.writeAsync(KEY, source);
        source[0] = 9;
        assertFalse(written.isDone());
        assertEquals(0, cache.metrics(KEY.layer()).writes());
        store.pendingWrite.complete(null);
        assertTrue(written.get());
        store.pendingRead = FdxFuture.pending();
        var loaded = cache.readAsync(KEY);
        assertFalse(loaded.isDone());
        store.pendingRead.complete(store.records.get(KEY.digest()));
        assertArrayEquals(new byte[]{1, 2, 3}, loaded.get());
        loaded.get()[0] = 5;
        store.pendingRead = null;
        assertArrayEquals(new byte[]{1, 2, 3}, cache.readAsync(KEY).get());
        assertEquals(2, cache.metrics(KEY.layer()).hits());
        assertEquals(1, cache.metrics(KEY.layer()).writes());
    }

    @Test
    void corruptTruncatedStaleAndWrongKeyRecordsBecomeMissesAndAreReplaced() {
        MemoryStore store = new MemoryStore();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        assertNull(cache.readAsync(KEY).get());
        cache.writeAsync(KEY, new byte[]{1, 2, 3});
        byte[] good = store.records.get(KEY.digest()).clone();
        int[] corruptOffsets = {0, 4, 5, 9, 73, good.length - 1};
        for (int offset : corruptOffsets) {
            byte[] bad = good.clone(); bad[offset] ^= 1;
            store.records.put(KEY.digest(), bad);
            assertNull(cache.readAsync(KEY).get());
        }
        store.records.put(KEY.digest(), new byte[0]);
        assertNull(cache.readAsync(KEY).get());
        assertEquals(7, cache.metrics(KEY.layer()).invalidEntries());
        assertEquals(8, cache.metrics(KEY.layer()).misses());
        cache.writeAsync(KEY, new byte[]{4});
        assertArrayEquals(new byte[]{4}, cache.readAsync(KEY).get());
    }

    @Test
    void unavailableAndDisabledStorageDoNotFailPreparation() {
        MemoryStore store = new MemoryStore();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        store.pendingRead = FdxFuture.failed(new IllegalStateException("read denied"));
        store.pendingWrite = FdxFuture.failed(new IllegalStateException("disk full"));
        assertNull(cache.readAsync(KEY).get());
        assertFalse(cache.writeAsync(KEY, new byte[]{1}).get());
        assertEquals(2, cache.metrics(KEY.layer()).storageFailures());
        ShaderArtifactCache disabled = new ShaderArtifactCache(null);
        assertFalse(disabled.enabled());
        assertNull(disabled.readAsync(KEY).get());
        assertFalse(disabled.writeAsync(KEY, new byte[]{1}).get());
        assertEquals(1, disabled.metrics(KEY.layer()).disabledReads());
        assertEquals(0, disabled.metrics(KEY.layer()).storageFailures());
    }

    @Test
    void mergeUsesLatestValidatedRecordAndOwnsIncomingUntilDeferredCompletion() {
        MemoryStore store = new MemoryStore();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        cache.writeAsync(KEY, new byte[]{1});
        store.deferUpdate = true;
        byte[] incoming = {2};
        var merged = cache.mergeAsync(KEY, incoming, (current, added) -> new byte[]{(byte)(current[0] | added[0])});
        incoming[0] = 32;
        cache.writeAsync(KEY, new byte[]{4});
        assertFalse(merged.isDone());
        store.completeUpdate();
        assertTrue(merged.get());
        assertArrayEquals(new byte[]{6}, cache.readAsync(KEY).get());
    }

    @Test
    void corruptAggregateIsRebuiltButMergeFailureNeverReplacesTheCurrentEntry() {
        MemoryStore store = new MemoryStore(); ShaderArtifactCache cache = new ShaderArtifactCache(store);
        store.records.put(KEY.digest(), new byte[]{0});
        assertTrue(cache.mergeAsync(KEY, new byte[]{7}, (current, added) -> {
            assertNull(current); return added;
        }).get());
        assertEquals(1, cache.metrics(KEY.layer()).invalidEntries());
        assertFalse(cache.mergeAsync(KEY, new byte[]{8}, (current, added) -> {
            throw new IllegalStateException("Native merge rejected");
        }).get());
        assertFalse(cache.mergeAsync(KEY, new byte[]{8}, (current, added) -> new byte[0]).get());
        assertArrayEquals(new byte[]{7}, cache.readAsync(KEY).get());
        assertEquals(2, cache.metrics(KEY.layer()).storageFailures());
    }

    @Test
    void unsupportedAtomicUpdatesNeverFallBackToPlainReplacement() {
        MemoryStore store = new MemoryStore(); store.atomic = false;
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        assertFalse(cache.supportsAtomicUpdate());
        assertFalse(cache.mergeAsync(KEY, new byte[]{1}, (old, added) -> {
            fail("Unsupported stores cannot merge"); return added;
        }).get());
        assertTrue(store.records.isEmpty());
    }

    private static final class MemoryStore implements ShaderCacheStore {
        final Map<String, byte[]> records = new HashMap<>();
        FdxFuture<byte[]> pendingRead;
        FdxFuture<Void> pendingWrite;
        boolean deferUpdate, atomic = true;
        FdxFuture<Void> pendingUpdate;
        Runnable update;
        @Override
        public boolean supportsAtomicUpdate() { return atomic; }
        @Override
        public FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> transform) {
            pendingUpdate = FdxFuture.pending();
            update = () -> {
                try { records.put(key, transform.apply(records.get(key))); pendingUpdate.complete(null); }
                catch (Throwable error) { pendingUpdate.completeExceptionally(error); }
            };
            if (!deferUpdate) completeUpdate();
            return pendingUpdate;
        }
        void completeUpdate() { update.run(); }
        @Override
        public FdxFuture<byte[]> readAsync(String key) {
            return pendingRead == null ? FdxFuture.completed(records.get(key)) : pendingRead;
        }
        @Override
        public FdxFuture<Void> writeAsync(String key, byte[] bytes) {
            records.put(key, bytes);
            return pendingWrite == null ? FdxFuture.completed(null) : pendingWrite;
        }
        @Override
        public FdxFuture<Void> removeAsync(String key) { records.remove(key); return FdxFuture.completed(null); }
    }
}
