package io.github.libfdx.backend.web;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Application-owned, origin-local IndexedDB artifact storage for JS and Wasm applications.
 * Use a stable database name private to this cache. Storage operations are asynchronous and
 * serialized; writes atomically replace entries and evict oldest writes to the byte budget and
 * 4096-entry limit, including across tabs. At most 256 operations may wait. Quota, blocked opens,
 * missing IndexedDB and transaction failures complete exceptionally; ShaderArtifactCache treats
 * them as misses. Browser eviction is permitted. Call from the browser application thread.
 * Disposal rejects new work and closes the database after accepted operations drain, without
 * waiting. Dispose only after preparation services drain. This store contains no native binaries.
 */
public final class WebShaderCacheStore implements ShaderCacheStore, Disposable {
    private final JSObject state;
    private boolean disposed;

    public WebShaderCacheStore(String databaseName, int maxBytes) {
        if (databaseName == null || databaseName.isBlank()) throw new IllegalArgumentException("Cache database name is empty");
        if (maxBytes < ShaderArtifactCache.MAX_RECORD_BYTES) {
            throw new IllegalArgumentException("Cache budget must hold at least one maximum-sized record");
        }
        state = create(databaseName, maxBytes, ShaderArtifactCache.MAX_RECORD_BYTES);
    }

    @Override
    public FdxFuture<byte[]> readAsync(String key) {
        requireKey(key);
        FdxFuture<byte[]> result = FdxFuture.pending();
        if (disposed) { result.completeExceptionally(closed()); return result; }
        enqueue(state, 0, key, null, (bytes, error) -> {
            if (error != null) result.completeExceptionally(new FdxException(error));
            else if (bytes == null) result.complete(null);
            else {
                byte[] copy = new byte[bytes.getLength()];
                for (int i = 0; i < copy.length; i++) copy[i] = (byte) bytes.get(i);
                result.complete(copy);
            }
        });
        return result;
    }

    @Override
    public FdxFuture<Void> writeAsync(String key, byte[] bytes) {
        requireKey(key);
        if (bytes == null || bytes.length < 1 || bytes.length > ShaderArtifactCache.MAX_RECORD_BYTES) {
            throw new IllegalArgumentException("Shader cache record size is out of bounds");
        }
        if (disposed) return failedClosed();
        Uint8Array copy = new Uint8Array(bytes.length);
        for (int i = 0; i < bytes.length; i++) copy.set(i, (short) (bytes[i] & 255));
        return operation(1, key, copy);
    }

    @Override
    public FdxFuture<Void> removeAsync(String key) {
        requireKey(key);
        return operation(2, key, null);
    }

    /** Completes after previously accepted I/O. Stop producers first for a final drain. */
    public FdxFuture<Void> flushAsync() { return operation(3, "", null); }

    private FdxFuture<Void> operation(int operation, String key, Uint8Array bytes) {
        if (disposed) return failedClosed();
        FdxFuture<Void> result = FdxFuture.pending();
        enqueue(state, operation, key, bytes, (ignored, error) -> {
            if (error == null) result.complete(null);
            else result.completeExceptionally(new FdxException(error));
        });
        return result;
    }

    private static FdxException closed() { return new FdxException("Web shader cache is closed"); }
    private static FdxFuture<Void> failedClosed() {
        FdxFuture<Void> result = FdxFuture.pending();
        result.completeExceptionally(closed());
        return result;
    }
    private static void requireKey(String key) {
        if (key == null || key.length() != 64) throw new IllegalArgumentException("Cache key must be a SHA-256 digest");
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) {
                throw new IllegalArgumentException("Cache key must be a lowercase SHA-256 digest");
            }
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        close(state);
    }
    @Override
    public boolean isDisposed() { return disposed; }

    @JSFunctor
    private interface Completion extends JSObject { void complete(Uint8Array bytes, String error); }

    @JSBody(params = {"name", "budget", "recordLimit"}, script = """
            return {name:name, budget:budget, recordLimit:recordLimit, pending:0,
                tail:Promise.resolve(), connection:null, opening:null};
            """)
    private static native JSObject create(String name, int budget, int recordLimit);

    @JSBody(params = {"state", "operation", "key", "bytes", "complete"}, script = """
            if (state.pending >= 256) { complete(null, 'Web shader cache queue is full'); return; }
            state.pending++;
            function open() {
                if (state.connection) return Promise.resolve(state.connection);
                if (state.opening) return state.opening;
                state.opening = new Promise(function(resolve, reject) {
                    var request = indexedDB.open(state.name, 1), failed = false;
                    request.onupgradeneeded = function() {
                        request.result.createObjectStore('artifacts');
                    };
                    request.onblocked = function() { failed = true; reject(new Error('Shader cache open is blocked')); };
                    request.onerror = function() { reject(request.error); };
                    request.onsuccess = function() {
                        var db = request.result;
                        if (failed) { db.close(); return; }
                        db.onversionchange = function() { db.close(); state.connection = null; state.opening = null; };
                        state.connection = db;
                        resolve(db);
                    };
                });
                return state.opening;
            }
            var work = state.tail.then(function() {
                if (operation === 3) return null;
                return open().then(function(db) {
                    return new Promise(function(resolve, reject) {
                        var tx = db.transaction('artifacts', operation === 0 ? 'readonly' : 'readwrite');
                        var store = tx.objectStore('artifacts'), result = null;
                        tx.oncomplete = function() { resolve(result); };
                        tx.onabort = function() { reject(tx.error || new Error('Shader cache transaction aborted')); };
                        tx.onerror = function() {}; // Abort delivers the single terminal failure.
                        if (operation === 0) {
                            var read = store.get(key);
                            read.onsuccess = function() {
                                var value = read.result;
                                if (value === undefined) return;
                                result = value && value.data instanceof Uint8Array && value.data.length > 0
                                    && value.data.length <= state.recordLimit ? value.data : new Uint8Array(0);
                            };
                        } else if (operation === 2) store.delete(key);
                        else {
                            // Scan one record at a time; retain only metadata while trimming.
                            var entries = [], total = bytes.length;
                            var scan = store.openCursor();
                            scan.onsuccess = function() {
                                var cursor = scan.result;
                                if (cursor) {
                                    if (cursor.key !== key) {
                                        var value = cursor.value;
                                        var size = value && value.data instanceof Uint8Array ? value.data.length : 0;
                                        if (size < 1 || size > state.recordLimit) cursor.delete();
                                        else {
                                            total += size;
                                            entries.push({key:cursor.key, size:size, stamp:value.stamp || 0});
                                        }
                                    }
                                    cursor.continue();
                                } else {
                                    entries.sort(function(a,b) { return a.stamp - b.stamp; });
                                    var count = entries.length + 1;
                                    for (var i = 0; i < entries.length && (total > state.budget || count > 4096); i++) {
                                        store.delete(entries[i].key); total -= entries[i].size; count--;
                                    }
                                    store.put({data:bytes, stamp:Date.now()}, key);
                                }
                            };
                        }
                    });
                });
            });
            state.tail = work.then(function() {}, function() { state.opening = null; });
            work.then(function(result) { state.pending--; complete(result || null, null); },
                function(error) { state.pending--; complete(null, String(error)); });
            """)
    private static native void enqueue(JSObject state, int operation, String key, Uint8Array bytes, Completion complete);

    @JSBody(params = "state", script = """
            state.tail.then(function() {
                if (state.connection) state.connection.close();
                state.connection = null; state.opening = null;
            });
            """)
    private static native void close(JSObject state);
}
