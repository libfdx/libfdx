package io.github.libfdx.backend.android;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationTrace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Shared, internally synchronized native cache. Jobs retain the native context throughout
 * initialization, compilation and snapshots. Context destruction releases the cache before the
 * device. Workers alone use this adapter; snapshot locking is never shared with rendering. */
final class AndroidVulkanPipelineCache {
    interface NativeAccess {
        void requireUsable();
        void observe(Throwable failure);
        byte[] identity();
        int initialize(byte[] bytes);
        byte[] snapshot();
        long[] statistics();
        byte[] merge(byte[] current, byte[] incoming);
        void retain();
        void release();
    }

    private final NativeAccess nativeCache;
    private final ShaderArtifactCache artifacts;
    private final Consumer<Runnable> execute;
    private final AtomicInteger active = new AtomicInteger(), completed = new AtomicInteger();
    private FdxFuture<AndroidVulkanPipelineCache> initialization;
    private ShaderCacheKey key;
    private boolean available;
    private byte[] lastSnapshot;
    private byte[] identity;
    private long reportedCreations, reportedFeedbacks, reportedHits, savedMutations;
    private PendingSave pendingSave;

    private static final class PendingSave {
        byte[] bytes, digest;
        PendingSave(byte[] bytes, byte[] digest) { this.bytes = bytes; this.digest = digest; }
    }

    AndroidVulkanPipelineCache(long context, ShaderArtifactCache artifacts, Consumer<Runnable> execute,
            AndroidVulkanDeviceState state) {
        this(new NativeAccess() {
            @Override
            public void requireUsable() { state.requireUsable(); }
            @Override
            public void observe(Throwable failure) { state.observe(failure); }
            @Override
            public byte[] identity() { return AndroidVulkanNative.pipelineCacheIdentity(context); }
            @Override
            public int initialize(byte[] bytes) { return AndroidVulkanNative.initializePipelineCache(context, bytes); }
            @Override
            public byte[] snapshot() { return AndroidVulkanNative.snapshotPipelineCache(context); }
            @Override
            public long[] statistics() { return AndroidVulkanNative.pipelineCacheStatistics(context); }
            @Override
            public byte[] merge(byte[] current, byte[] incoming) {
                return AndroidVulkanNative.mergePipelineCaches(context, current, incoming);
            }
            @Override
            public void retain() { AndroidVulkanNative.retainPreparationDevice(context); }
            @Override
            public void release() { AndroidVulkanNative.releasePreparationDevice(context); }
        }, artifacts, execute);
    }

    AndroidVulkanPipelineCache(NativeAccess nativeCache, ShaderArtifactCache artifacts, Consumer<Runnable> execute) {
        this.nativeCache = nativeCache; this.artifacts = artifacts; this.execute = execute;
    }

    synchronized FdxFuture<AndroidVulkanPipelineCache> initializeAsync() {
        if (initialization != null) return initialization;
        initialization = FdxFuture.pending();
        if (artifacts == null || !artifacts.supportsAtomicUpdate()) {
            initialization.complete(this);
            return initialization;
        }
        try {
            nativeCache.requireUsable();
            identity = nativeCache.identity();
            key = key(identity);
            ShaderPreparationTrace trace = ShaderPreparationTrace.current();
            Consumer<Runnable> initializeExecutor = trace == null ? execute : trace.executor(execute);
            artifacts.readAsync(key).onSuccess(bytes -> {
                try { initializeExecutor.accept(() -> initialize(bytes)); }
                catch (RuntimeException | Error failure) { initialization.completeExceptionally(failure); }
            }).onFailure(initialization::completeExceptionally);
        } catch (GraphicsContextLostException lost) {
            nativeCache.observe(lost); initialization.completeExceptionally(lost);
        } catch (RuntimeException unavailable) {
            key = null;
            initialization.complete(this);
        } catch (Error failure) { initialization.completeExceptionally(failure); }
        return initialization;
    }

    static ShaderCacheKey key(byte[] identity) {
        if (identity == null || identity.length != 36) throw new IllegalArgumentException("Vulkan cache identity is unavailable");
        ByteBuffer fields = ByteBuffer.wrap(identity).order(ByteOrder.LITTLE_ENDIAN);
        int pointerBytes = fields.getInt(16);
        if (pointerBytes != 4 && pointerBytes != 8) throw new IllegalArgumentException("Unknown Vulkan native ABI");
        return ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "android-vulkan-pipeline-cache:1", Arrays.toString(identity));
    }

    private void initialize(byte[] bytes) {
        try {
            nativeCache.requireUsable();
            int result = nativeCache.initialize(bytes);
            available = result != 0;
            if (result == 2) artifacts.rejected(key);
            if (available && result == 1 && bytes != null) lastSnapshot = digest(bytes);
            initialization.complete(this);
        } catch (GraphicsContextLostException lost) {
            nativeCache.observe(lost); initialization.completeExceptionally(lost);
        } catch (RuntimeException unavailable) { initialization.complete(this); }
        catch (Error failure) { initialization.completeExceptionally(failure); }
    }

    void beginNative() { nativeCache.requireUsable(); active.incrementAndGet(); }

    void endNative() {
        int remaining = active.decrementAndGet();
        int count = completed.incrementAndGet();
        if (key != null && (remaining == 0 || count % 32 == 0)) snapshot();
    }

    private synchronized void snapshot() {
        try {
            nativeCache.requireUsable();
            long[] statistics = nativeCache.statistics();
            // Native counters cover every concurrent job. Keep them aggregate-only; attributing
            // their differences to the job triggering this snapshot would invent per-entry data.
            ShaderPreparationTrace trace = ShaderPreparationTrace.current();
            ShaderPreparationTrace.restore(null);
            try {
            while (reportedCreations < statistics[0]) {
                artifacts.pipelineInvoked(key, available);
                reportedCreations++;
            }
            while (reportedFeedbacks < statistics[1]) {
                boolean hit = reportedHits < statistics[2];
                artifacts.pipelineFeedback(key, hit);
                reportedFeedbacks++;
                if (hit) reportedHits++;
            }
            } finally { ShaderPreparationTrace.restore(trace); }
            if (!available || savedMutations == statistics[3]) return;
            byte[] bytes = nativeCache.snapshot();
            if (bytes == null) return; // Native size bound or VK_INCOMPLETE; another completed job can retry.
            byte[] digest = digest(bytes);
            savedMutations = statistics[3];
            if (Arrays.equals(lastSnapshot, digest)) return;
            lastSnapshot = digest;
            // Coalesce before the accepted storage transaction starts; do not schedule follow-up
            // saves from completion callbacks after the application has begun draining the store.
            if (pendingSave != null) {
                pendingSave.bytes = bytes; pendingSave.digest = digest;
                return;
            }
            nativeCache.retain();
            PendingSave pending = new PendingSave(bytes, digest);
            pendingSave = pending;
            FdxFuture<Boolean> save;
            try {
                save = artifacts.mergeAsync(key, bytes, (current, incoming) -> {
                    synchronized (AndroidVulkanPipelineCache.this) {
                        incoming = pending.bytes;
                        pendingSave = null;
                    }
                    return merge(current, incoming);
                });
            } catch (RuntimeException | Error failure) {
                pendingSave = null; nativeCache.release(); throw failure;
            }
            save.onSuccess(written -> {
                try {
                    if (!written) synchronized (AndroidVulkanPipelineCache.this) {
                        if (pendingSave == pending) pendingSave = null;
                        if (lastSnapshot == pending.digest) { lastSnapshot = null; savedMutations = -1; }
                    }
                } finally { nativeCache.release(); }
            });
        } catch (GraphicsContextLostException lost) {
            nativeCache.observe(lost); throw lost;
        } catch (RuntimeException unavailable) {
            // Export cannot invalidate a usable native pipeline. Future completions may retry.
            lastSnapshot = null;
            savedMutations = -1;
        }
    }

    private byte[] merge(byte[] current, byte[] incoming) {
        nativeCache.requireUsable();
        if (current == null || Arrays.equals(current, incoming)) return incoming;
        if (!validHeader(current, identity)) { artifacts.rejected(key); return incoming; }
        byte[] merged;
        try { merged = nativeCache.merge(current, incoming); }
        catch (GraphicsContextLostException lost) { nativeCache.observe(lost); throw lost; }
        if (merged == null) throw new FdxException("Vulkan cache merge unavailable or exceeds the storage bound");
        return merged;
    }

    static boolean validHeader(byte[] bytes, byte[] identity) {
        if (bytes == null || bytes.length < 32 || identity == null || identity.length != 36) return false;
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer fields = ByteBuffer.wrap(identity).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt(0) != 32 || header.getInt(4) != 1
                || header.getInt(8) != fields.getInt(0) || header.getInt(12) != fields.getInt(4)) return false;
        for (int i = 0; i < 16; i++) if (bytes[16 + i] != identity[20 + i]) return false;
        return true;
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
