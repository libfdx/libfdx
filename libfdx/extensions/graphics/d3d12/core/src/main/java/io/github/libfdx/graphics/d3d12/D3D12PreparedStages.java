package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.LinkedHashMap;

/** Provider-local shared stages. Only workers wait; no compiler runs while holding the cache lock. */
final class D3D12PreparedStages implements AutoCloseable {
    private record Key(String source, String entry, String profile) { }
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(32, .75f, true);
    private final LinkedHashMap<ShaderCacheKey, Boolean> rejectedKeys = new LinkedHashMap<>();
    private final boolean validation, optimize;
    private final int capacity;
    private final ShaderArtifactCache cache;
    private boolean closed;

    D3D12PreparedStages(boolean validation, boolean optimize, int capacity, ShaderArtifactCache cache) {
        this.validation = validation;
        this.optimize = optimize;
        this.capacity = capacity;
        this.cache = cache;
    }

    ShaderCacheKey cacheKey(String source, String entryPoint, String profile) {
        return ShaderCacheKey.of(ShaderCacheLayer.DXIL, "validated-dxil-v1", D3D12DxcCompiler.cacheIdentity(),
                source, entryPoint, profile,
                D3D12DxcCompiler.arguments(entryPoint, profile, validation, optimize).toString());
    }

    Lease acquire(String source, String entryPoint, String profile, String label,
            ShaderCacheKey cacheKey, byte[] cachedBytes) {
        Key key = new Key(source, entryPoint, profile);
        Entry entry;
        boolean compile;
        boolean allowPersisted;
        synchronized (entries) {
            if (closed) throw new FdxException("D3D12 preparation stage cache is closed");
            entry = entries.get(key);
            compile = entry == null;
            if (compile) { entry = new Entry(); entries.put(key, entry); }
            entry.references++;
            allowPersisted = cacheKey != null && !rejectedKeys.containsKey(cacheKey);
        }
        if (compile) {
            try {
                MemorySegment blob;
                if (cachedBytes != null && allowPersisted) {
                    blob = D3D12DxcCompiler.workerCompiler().loadBytecode(cachedBytes);
                    entry.persisted = true;
                } else {
                    if (cacheKey != null) cache.compilerInvoked(cacheKey);
                    blob = D3D12DxcCompiler.workerCompiler().compile(source, entryPoint, profile, label, validation, optimize);
                }
                entry.blob = MemorySegment.ofAddress(blob.address());
                if (!entry.persisted && cacheKey != null) {
                    // Successful DXC output is validated. I/O owns copied bytes, never this COM reference.
                    if (D3D12DxcCompiler.size(blob) <= ShaderArtifactCache.MAX_PAYLOAD_BYTES) {
                        cache.writeAsync(cacheKey, D3D12DxcCompiler.bytecode(blob));
                    }
                }
                entry.completion.complete(entry.blob);
            } catch (Throwable failure) {
                entry.completion.completeExceptionally(failure);
                synchronized (entries) {
                    entries.remove(key, entry);
                    entry.cached = false;
                }
            }
        }
        try {
            // The creating worker compiles inline; this never depends on a queued task in our pool.
            MemorySegment blob = entry.completion.join();
            return new Lease(entry, blob);
        } catch (CompletionException failure) {
            release(entry);
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException error) throw error;
            throw new FdxException("D3D12 stage preparation failed", cause);
        }
    }

    /** A cached container can still be rejected by the driver. Retire only this exact shared entry. */
    void reject(Lease lease, ShaderCacheKey key) {
        synchronized (entries) {
            if (lease.entry == null || !lease.entry.persisted || !lease.entry.cached) return;
            entries.values().remove(lease.entry);
            lease.entry.cached = false;
            if (key != null) {
                rejectedKeys.put(key, true);
                while (rejectedKeys.size() > capacity) rejectedKeys.remove(rejectedKeys.keySet().iterator().next());
            }
        }
        if (key != null) cache.rejected(key);
    }

    private void release(Entry entry) {
        ArrayList<MemorySegment> retired = new ArrayList<>();
        synchronized (entries) {
            if (--entry.references < 0) throw new IllegalStateException("Unbalanced DXIL lease");
            if (!entry.cached && entry.references == 0) retire(entry, retired);
            var iterator = entries.entrySet().iterator();
            while (entries.size() > capacity && iterator.hasNext()) {
                Entry candidate = iterator.next().getValue();
                if (candidate.references == 0 && candidate.completion.isDone()) {
                    iterator.remove();
                    candidate.cached = false;
                    retire(candidate, retired);
                }
            }
        }
        for (MemorySegment blob : retired) D3D12Ffm.release(blob);
    }

    private static void retire(Entry entry, ArrayList<MemorySegment> retired) {
        if (entry.blob.address() != 0) {
            retired.add(entry.blob);
            entry.blob = MemorySegment.NULL;
        }
    }

    @Override public void close() {
        ArrayList<MemorySegment> retired = new ArrayList<>();
        synchronized (entries) {
            if (closed) return;
            closed = true;
            for (Entry entry : entries.values()) {
                entry.cached = false;
                if (entry.references == 0) retire(entry, retired);
            }
            entries.clear();
            rejectedKeys.clear();
        }
        for (MemorySegment blob : retired) D3D12Ffm.release(blob);
    }

    final class Lease implements AutoCloseable {
        private Entry entry;
        final MemorySegment blob;
        Lease(Entry entry, MemorySegment blob) { this.entry = entry; this.blob = blob; }
        boolean persisted() { return entry != null && entry.persisted; }
        @Override public void close() {
            if (entry == null) return;
            Entry releasing = entry;
            entry = null;
            release(releasing);
        }
    }

    private static final class Entry {
        final CompletableFuture<MemorySegment> completion = new CompletableFuture<>();
        volatile MemorySegment blob = MemorySegment.NULL;
        int references;
        boolean cached = true;
        boolean persisted;
    }
}
