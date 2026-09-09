package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.internal.PortableSha256;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BinaryOperator;

/**
 * Optional, borrowed asynchronous artifact store with versioned, key-bound checksummed records.
 * All failures are cache misses or failed writes; callers continue normal preparation. A null
 * store disables persistence for reproducible benchmarks. This object does not dispose its store.
 *
 * <p>Invoke from preparation workers or explicit loading on single-threaded targets: encoding and
 * validation hash/copy artifact bytes. Completion
 * callbacks run on the store's completion thread (or inline for disabled/rejected operations),
 * never implicitly on the application thread. Providers must schedule native work and publication
 * according to their own execution policy. Never wait on these futures in rendering.</p>
 */
public final class ShaderArtifactCache {
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    public static final int MAX_RECORD_BYTES = MAX_PAYLOAD_BYTES + 137;
    private static final int HEADER_BYTES = 137;
    private final ShaderCacheStore store;
    private final long[][] counters;
    private final boolean bypassReads;

    /** A snapshot for one layer; misses include disabled, invalid and unavailable reads. */
    public record Metrics(long hits, long misses, long writes, long invalidEntries,
            long storageFailures, long disabledReads, long compilerInvocations,
            long pipelineCreations, long pipelineCreationsWithoutCache,
            long pipelineFeedbacks, long pipelineCacheHits) { }

    public ShaderArtifactCache(ShaderCacheStore store) {
        this(store, new long[ShaderCacheLayer.values().length][11], false);
    }
    private ShaderArtifactCache(ShaderCacheStore store, long[][] counters, boolean bypassReads) {
        this.store = store; this.counters = counters; this.bypassReads = bypassReads;
    }
    public boolean enabled() { return store != null; }

    /** Whether aggregate artifacts can be merged without overwriting another writer's updates. */
    public boolean supportsAtomicUpdate() { return store != null && store.supportsAtomicUpdate(); }

    /**
     * Borrowed view that bypasses reads and replaces successfully rebuilt artifacts in the same
     * store. Metrics and lifetime are shared. Providers use this for one bounded retry after a
     * downstream compiler/driver rejects restored data; the view does not delete unrelated records.
     */
    public ShaderArtifactCache refreshing() { return new ShaderArtifactCache(store, counters, true); }

    public Metrics metrics(ShaderCacheLayer layer) {
        synchronized (counters) {
            long[] counts = counters[layer.ordinal()];
            return new Metrics(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7], counts[8], counts[9], counts[10]);
        }
    }

    public FdxFuture<byte[]> readAsync(ShaderCacheKey key) {
        ShaderPreparationTrace trace = ShaderPreparationTrace.current();
        long started = System.nanoTime();
        if (key == null) throw new NullPointerException("key");
        if (store == null) {
            count(key, 1, trace); count(key, 5, trace);
            return FdxFuture.completed(null);
        }
        if (bypassReads) { count(key, 1, trace); return FdxFuture.completed(null); }
        FdxFuture<byte[]> result = FdxFuture.pending();
        if (trace != null) result.onSuccess(ignored -> trace.cacheElapsed(key.layer(), false, started));
        FdxFuture<byte[]> pending;
        try { pending = store.readAsync(key.digest()); }
        catch (RuntimeException failure) { readFailed(key, result, trace); return result; }
        pending.onSuccess(record -> {
                byte[] payload = record == null ? null : decode(key, record);
                if (payload != null) count(key, 0, trace);
                else {
                    count(key, 1, trace);
                    if (record != null) count(key, 3, trace);
                    // Do not remove here: another process may already have replaced the bad entry.
                    // A successful compile will atomically replace it with a validated record.
                }
                complete(result, payload, trace);
            }).onFailure(failure -> readFailed(key, result, trace));
        return result;
    }

    /** Copies payload into its envelope before submitting. True means the atomic write completed. */
    public FdxFuture<Boolean> writeAsync(ShaderCacheKey key, byte[] payload) {
        ShaderPreparationTrace trace = ShaderPreparationTrace.current();
        long started = System.nanoTime();
        if (key == null || payload == null) throw new NullPointerException("cache artifact");
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Shader cache payload size is out of bounds");
        }
        if (store == null) return FdxFuture.completed(false);
        byte[] record = encode(key, payload);
        FdxFuture<Boolean> result = FdxFuture.pending();
        if (trace != null) result.onSuccess(ignored -> trace.cacheElapsed(key.layer(), true, started));
        FdxFuture<Void> pending;
        try { pending = store.writeAsync(key.digest(), record); }
        catch (RuntimeException failure) { writeFailed(key, result, trace); return result; }
        pending.onSuccess(ignored -> {
                count(key, 2, trace); complete(result, true, trace);
            }).onFailure(failure -> writeFailed(key, result, trace));
        return result;
    }

    /**
     * Copies incoming, then merges it with the latest validated payload inside a storage write
     * transaction. Corrupt/missing records are supplied to merge as null; refreshing views still
     * read the latest aggregate to preserve other writers. Merge runs on the storage worker and
     * must return a nonempty bounded payload or throw to leave the entry unchanged. It must not
     * access an unretained device, render-owned state or this store's I/O. False means unsupported
     * or failed persistence; there is no separate-read/write fallback. Resources used by merge
     * must remain alive until completion, including after preparation or store disposal begins.
     */
    public FdxFuture<Boolean> mergeAsync(ShaderCacheKey key, byte[] incoming, BinaryOperator<byte[]> merge) {
        ShaderPreparationTrace trace = ShaderPreparationTrace.current();
        long started = System.nanoTime();
        if (key == null || incoming == null || merge == null) throw new NullPointerException("cache merge");
        requirePayload(incoming);
        if (!supportsAtomicUpdate()) return FdxFuture.completed(false);
        byte[] owned = incoming.clone();
        FdxFuture<Boolean> result = FdxFuture.pending();
        if (trace != null) result.onSuccess(ignored -> trace.cacheElapsed(key.layer(), true, started));
        FdxFuture<Void> pending;
        try {
            pending = store.updateAsync(key.digest(), record -> {
                ShaderPreparationTrace previous = trace == null ? ShaderPreparationTrace.current() : trace.attach();
                try {
                    byte[] current = record == null ? null : decode(key, record);
                    if (current != null) count(key, 0, trace);
                    else { count(key, 1, trace); if (record != null) count(key, 3, trace); }
                    byte[] merged = merge.apply(current, owned);
                    requirePayload(merged);
                    return encode(key, merged);
                } finally { ShaderPreparationTrace.restore(previous); }
            });
        } catch (RuntimeException failure) { writeFailed(key, result, trace); return result; }
        pending.onSuccess(ignored -> { count(key, 2, trace); complete(result, true, trace); })
                .onFailure(failure -> writeFailed(key, result, trace));
        return result;
    }

    private static void requirePayload(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("Shader cache payload size is out of bounds");
    }

    private static byte[] encode(ShaderCacheKey key, byte[] payload) {
        byte[] record = new byte[HEADER_BYTES + payload.length];
        record[0] = 'F'; record[1] = 'D'; record[2] = 'X'; record[3] = 'S';
        record[4] = 1;
        for (int i = 0; i < 4; i++) record[5 + i] = (byte)(payload.length >>> (i * 8));
        byte[] identity = key.digest().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(identity, 0, record, 9, 64);
        byte[] checksum = PortableSha256.hash(payload).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(checksum, 0, record, 73, 64);
        System.arraycopy(payload, 0, record, HEADER_BYTES, payload.length);
        return record;
    }

    /** Records a compiler/driver rejection of a well-formed cached payload, before compiling anew. */
    public void rejected(ShaderCacheKey key) { count(key, 3); }

    /** Provider hook immediately before invoking the compiler for this identified artifact. */
    public void compilerInvoked(ShaderCacheKey key) { count(key, 6); }

    /** Records an explicit native pipeline creation call, separately from shader compiler calls.
     * Providing cache data does not establish an internal driver hit or exclude residual work. */
    public void pipelineInvoked(ShaderCacheKey key, boolean cacheProvided) {
        count(key, 7);
        if (!cacheProvided) count(key, 8);
    }

    /** Records valid feedback returned by a native pipeline API. No feedback is unavailable,
     * not a miss; only the driver's explicit application-cache-hit flag counts as a hit. */
    public void pipelineFeedback(ShaderCacheKey key, boolean cacheHit) {
        count(key, 9);
        if (cacheHit) count(key, 10);
    }

    private void readFailed(ShaderCacheKey key, FdxFuture<byte[]> result, ShaderPreparationTrace trace) {
        count(key, 1, trace); count(key, 4, trace); complete(result, null, trace);
    }
    private void writeFailed(ShaderCacheKey key, FdxFuture<Boolean> result, ShaderPreparationTrace trace) {
        count(key, 4, trace); complete(result, false, trace);
    }
    private static <T> void complete(FdxFuture<T> result, T value, ShaderPreparationTrace trace) {
        ShaderPreparationTrace previous = trace == null ? ShaderPreparationTrace.current() : trace.attach();
        try { result.complete(value); } finally { ShaderPreparationTrace.restore(previous); }
    }
    private void count(ShaderCacheKey key, int metric) {
        count(key, metric, ShaderPreparationTrace.current());
    }
    private void count(ShaderCacheKey key, int metric, ShaderPreparationTrace trace) {
        synchronized (counters) { counters[key.layer().ordinal()][metric]++; }
        if (trace != null) trace.count(key.layer(), metric);
    }

    private static byte[] decode(ShaderCacheKey key, byte[] record) {
        if (record.length <= HEADER_BYTES || record.length > MAX_RECORD_BYTES
                || record[0] != 'F' || record[1] != 'D' || record[2] != 'X' || record[3] != 'S'
                || record[4] != 1) return null;
        int length = 0;
        for (int i = 0; i < 4; i++) length |= (record[5 + i] & 255) << (i * 8);
        if (length != record.length - HEADER_BYTES) return null;
        for (int i = 0; i < 64; i++) if (record[9 + i] != key.digest().charAt(i)) return null;
        String checksum = new PortableSha256().update(record, HEADER_BYTES, length).digestHex();
        for (int i = 0; i < 64; i++) if (record[73 + i] != checksum.charAt(i)) return null;
        return Arrays.copyOfRange(record, HEADER_BYTES, record.length);
    }
}
