package io.github.libfdx.backend.android;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AndroidVulkanPipelineCacheTest {
    @ParameterizedTest
    @ValueSource(strings = {"identity", "initialize", "snapshot", "merge"})
    void nativeLossIsNotAnOptionalCacheMiss(String stage) {
        Store store = new Store(); store.read.complete(null);
        NativeCache nativeCache = new NativeCache();
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        var cache = new AndroidVulkanPipelineCache(nativeCache, artifacts, Runnable::run);
        if (stage.equals("identity") || stage.equals("initialize")) {
            nativeCache.failureAt = stage;
            var initialization = cache.initializeAsync();
            assertTrue(initialization.isFailed());
            assertThrows(GraphicsContextLostException.class, initialization::get);
        } else {
            cache.initializeAsync().get();
            if (stage.equals("merge")) {
                cache.beginNative(); nativeCache.creations++; cache.endNative();
                nativeCache.snapshot[32] = 1;
            }
            nativeCache.failureAt = stage;
            cache.beginNative(); nativeCache.creations++;
            if (stage.equals("snapshot")) assertThrows(GraphicsContextLostException.class, cache::endNative);
            else {
                cache.endNative(); // The store reports its failed optional transaction, but the device signal survives.
                assertEquals(1, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).storageFailures());
            }
        }
        assertTrue(nativeCache.lost);
        assertEquals(0, nativeCache.references);
        assertThrows(GraphicsContextLostException.class, cache::beginNative);
    }
    @Test
    void nativeInitializationWaitsForStorageAndPublishesOnceToWorkers() {
        Store store = new Store();
        NativeCache nativeCache = new NativeCache();
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        AndroidVulkanPipelineCache cache = new AndroidVulkanPipelineCache(nativeCache, artifacts, work::add);
        var initialized = cache.initializeAsync();
        assertSame(initialized, cache.initializeAsync());
        assertEquals(1, store.reads);
        assertFalse(initialized.isDone());
        assertEquals(0, nativeCache.initializations);
        assertTrue(work.isEmpty(), "Storage latency must leave compiler workers available");
        store.read.complete(null);
        assertEquals(0, nativeCache.initializations, "Storage callbacks must not call Vulkan");
        work.remove().run();
        assertSame(cache, initialized.get());
        assertEquals(1, nativeCache.initializations);

        cache.beginNative(); cache.beginNative(); nativeCache.creations = 2;
        cache.endNative();
        assertEquals(0, store.writes);
        cache.endNative();
        assertEquals(1, store.writes);
        assertEquals(2, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).pipelineCreations());
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(1, store.writes, "An unchanged driver snapshot must not be written again");

        Store freshStore = new Store(); freshStore.read.complete(store.record);
        ShaderArtifactCache freshArtifacts = new ShaderArtifactCache(freshStore);
        NativeCache freshNative = new NativeCache();
        AndroidVulkanPipelineCache fresh = new AndroidVulkanPipelineCache(freshNative, freshArtifacts, work::add);
        fresh.initializeAsync(); work.remove().run();
        assertArrayEquals(nativeCache.snapshot, freshNative.restored);
        fresh.beginNative(); freshNative.creations++; fresh.endNative();
        assertEquals(0, freshStore.writes);
        assertEquals(1, freshArtifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).hits());
    }

    @Test
    void rejectionRewritesTheDriverRecordAndAllocationFailureLeavesCompilationAvailable() {
        Store store = new Store();
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        NativeCache nativeCache = new NativeCache(); nativeCache.result = 2;
        var key = AndroidVulkanPipelineCache.key(nativeCache.identity());
        artifacts.writeAsync(key, new byte[]{1, 2, 3}).get(); store.read.complete(store.record);
        AndroidVulkanPipelineCache cache = new AndroidVulkanPipelineCache(nativeCache, artifacts, Runnable::run);
        assertTrue(cache.initializeAsync().isDone());
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(2, artifacts.metrics(key.layer()).invalidEntries(), "Initialization and the locked merge each reject the bad record");
        assertEquals(2, store.writes);

        Store failedStore = new Store(); failedStore.read.complete(null);
        ShaderArtifactCache failedArtifacts = new ShaderArtifactCache(failedStore);
        NativeCache allocationFailure = new NativeCache(); allocationFailure.result = 0;
        AndroidVulkanPipelineCache uncached = new AndroidVulkanPipelineCache(allocationFailure, failedArtifacts, Runnable::run);
        assertTrue(uncached.initializeAsync().isDone());
        uncached.beginNative(); allocationFailure.creations++; uncached.endNative();
        assertEquals(1, failedArtifacts.metrics(key.layer()).pipelineCreationsWithoutCache());
        assertEquals(0, failedStore.writes);
    }

    @Test
    void closedExecutorFailsThePendingInitializationWithoutCallingNativeCode() {
        Store store = new Store(); NativeCache nativeCache = new NativeCache();
        AndroidVulkanPipelineCache cache = new AndroidVulkanPipelineCache(nativeCache, new ShaderArtifactCache(store),
                task -> { throw new RejectedExecutionException("closed"); });
        var initialized = cache.initializeAsync();
        store.read.complete(null);
        assertTrue(initialized.isFailed());
        assertEquals(0, nativeCache.initializations);
    }

    @Test
    void confirmedDriverHitsAvoidNativeSnapshotCopies() {
        Store store = new Store(); store.read.complete(null);
        NativeCache nativeCache = new NativeCache(); nativeCache.hit = true;
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        AndroidVulkanPipelineCache cache = new AndroidVulkanPipelineCache(nativeCache, artifacts, Runnable::run);
        cache.initializeAsync().get();
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(0, nativeCache.snapshots);
        assertEquals(0, store.writes);
        assertEquals(1, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).pipelineFeedbacks());
        assertEquals(1, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).pipelineCacheHits());
    }

    @Test
    void identityIncludesVendorDeviceDriverApiPointerSizeAndEveryUuidByte() {
        byte[] identity = new NativeCache().identity();
        var original = AndroidVulkanPipelineCache.key(identity);
        for (int offset : new int[]{0, 4, 8, 12, 16, 20, 35}) {
            byte[] changed = identity.clone(); changed[offset] = (byte)(offset == 16 ? 4 : changed[offset] + 1);
            assertNotEquals(original, AndroidVulkanPipelineCache.key(changed));
        }
        assertThrows(IllegalArgumentException.class, () -> AndroidVulkanPipelineCache.key(new byte[36]));
        assertThrows(IllegalArgumentException.class, () -> AndroidVulkanPipelineCache.key(Arrays.copyOf(identity, 35)));
    }

    @Test
    void pendingMergeRetainsDeviceUntilStorageCompletesAndFailurePermitsRetry() {
        Store store = new Store(); store.read.complete(null); store.holdUpdate = true;
        NativeCache nativeCache = new NativeCache();
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        AndroidVulkanPipelineCache cache = new AndroidVulkanPipelineCache(nativeCache, artifacts, Runnable::run);
        cache.initializeAsync().get();
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(1, nativeCache.references);
        store.pendingUpdate.completeExceptionally(new IllegalStateException("closed storage"));
        assertEquals(0, nativeCache.references);
        assertEquals(1, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).storageFailures());
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(1, nativeCache.references);
        nativeCache.snapshot[32] = 9;
        cache.beginNative(); nativeCache.creations++; cache.endNative();
        assertEquals(1, nativeCache.references, "Queued snapshots coalesce into the already accepted save");
        store.finishUpdate();
        assertEquals(0, nativeCache.references);
        assertEquals(1, artifacts.metrics(ShaderCacheLayer.DRIVER_PIPELINE).writes());
        Store fresh = new Store(); fresh.read.complete(store.record);
        assertEquals(9, new ShaderArtifactCache(fresh).readAsync(AndroidVulkanPipelineCache.key(nativeCache.identity())).get()[32]);
    }

    @Test
    void independentNativeSnapshotsMergeAgainstLatestStoreContents() {
        Store store = new Store(); store.read.complete(null);
        NativeCache first = new NativeCache(), second = new NativeCache();
        first.snapshot[32] = 1; second.snapshot[32] = 2;
        ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        AndroidVulkanPipelineCache a = new AndroidVulkanPipelineCache(first, artifacts, Runnable::run);
        AndroidVulkanPipelineCache b = new AndroidVulkanPipelineCache(second, artifacts, Runnable::run);
        a.initializeAsync().get(); b.initializeAsync().get();
        a.beginNative(); first.creations++; a.endNative();
        b.beginNative(); second.creations++; b.endNative();
        // read was completed before initialization; inspect the stored envelope using a fresh view.
        Store fresh = new Store(); fresh.read.complete(store.record);
        byte[] merged = new ShaderArtifactCache(fresh).readAsync(AndroidVulkanPipelineCache.key(first.identity())).get();
        assertEquals(3, merged[32]); assertEquals(1, second.merges);
        assertEquals(0, first.references); assertEquals(0, second.references);
        assertTrue(AndroidVulkanPipelineCache.validHeader(merged, first.identity()));
        merged[16] = 1;
        assertFalse(AndroidVulkanPipelineCache.validHeader(merged, first.identity()));
    }

    private static final class NativeCache implements AndroidVulkanPipelineCache.NativeAccess {
        String failureAt;
        boolean lost;
        private void fail(String stage) {
            if (stage.equals(failureAt)) throw new GraphicsContextLostException(AndroidVulkanProvider.ID);
        }
        @Override
        public void requireUsable() { if (lost) throw new GraphicsContextLostException(AndroidVulkanProvider.ID); }
        @Override
        public void observe(Throwable failure) { if (failure instanceof GraphicsContextLostException) lost = true; }
        int initializations, snapshots, result = 1;
        int references, merges;
        boolean hit;
        long creations;
        byte[] restored;
        final byte[] snapshot = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(32).putInt(1).putInt(1).putInt(2).array();
        @Override
        public byte[] identity() {
            fail("identity");
            return ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(1).putInt(2).putInt(3).putInt(4).putInt(8).array();
        }
        @Override
        public int initialize(byte[] bytes) { fail("initialize"); initializations++; restored = bytes; return result; }
        @Override
        public byte[] snapshot() { fail("snapshot"); snapshots++; return snapshot; }
        @Override
        public void retain() { references++; }
        @Override
        public void release() { assertTrue(references > 0); references--; }
        @Override
        public byte[] merge(byte[] current, byte[] incoming) {
            fail("merge");
            assertTrue(references > 0); merges++;
            byte[] result = incoming.clone(); result[32] |= current[32]; return result;
        }
        @Override
        public long[] statistics() { return new long[]{creations, hit ? creations : 0, hit ? creations : 0, hit ? 0 : creations}; }
    }

    private static final class Store implements ShaderCacheStore {
        final FdxFuture<byte[]> read = FdxFuture.pending();
        int reads, writes;
        byte[] record;
        boolean holdUpdate;
        FdxFuture<Void> pendingUpdate;
        UnaryOperator<byte[]> update;
        @Override
        public boolean supportsAtomicUpdate() { return true; }
        @Override
        public FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> transform) {
            update = transform; pendingUpdate = FdxFuture.pending();
            if (!holdUpdate) finishUpdate();
            return pendingUpdate;
        }
        void finishUpdate() {
            try { record = update.apply(record); writes++; pendingUpdate.complete(null); }
            catch (Throwable error) { pendingUpdate.completeExceptionally(error); }
        }
        @Override
        public FdxFuture<byte[]> readAsync(String key) { reads++; return read; }
        @Override
        public FdxFuture<Void> writeAsync(String key, byte[] bytes) { writes++; record = bytes.clone(); return FdxFuture.completed(null); }
        @Override
        public FdxFuture<Void> removeAsync(String key) { return FdxFuture.completed(null); }
    }
}
