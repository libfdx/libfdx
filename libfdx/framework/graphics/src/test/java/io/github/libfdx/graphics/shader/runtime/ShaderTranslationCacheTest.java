package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.internal.ShaderTranslationCache;
import io.github.libfdx.graphics.shader.reflection.ShaderReflectionDecoderTest;
import io.github.libfdx.runtime.core.shader.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;

final class ShaderTranslationCacheTest {
    private static final RuntimeShaderCompileRequest REQUEST = RuntimeShaderCompileRequest
            .builder("wgsl input", RuntimeShaderCompileTarget.WGPU_WGSL).build();

    @Test void asyncCompilerMissRemainsPendingAndPersistsOnlyAfterValidatedCompletion() {
        Store store = new Store();
        FdxFuture<RuntimeShaderCompileResult> pending = FdxFuture.pending();
        RuntimeShaderCompiler compiler = asynchronousCompiler(pending);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        var cache = new ShaderTranslationCache(compiler, new ShaderArtifactCache(store));
        var result = cache.compileAsync(REQUEST, work::add);
        drain(work);
        assertFalse(result.isDone());
        assertTrue(store.records.isEmpty());
        pending.complete(RuntimeShaderCompileResult.text(REQUEST.source(), ShaderReflectionDecoderTest.runtimeFixture()));
        assertFalse(result.isDone()); // Completion is marshalled onto the preparation executor.
        drain(work);
        assertTrue(result.get().success());
        assertEquals(1, store.records.size());
        var replay = cache.compileAsync(REQUEST, work::add);
        drain(work);
        assertArrayEquals(result.get().reflection().bytes(), replay.get().reflection().bytes());
    }

    @Test void asyncCompilerFailurePropagatesWithoutWritingCache() {
        Store store = new Store();
        FdxFuture<RuntimeShaderCompileResult> pending = FdxFuture.pending();
        var compiler = asynchronousCompiler(pending);
        var cached = new ShaderTranslationCache(compiler, new ShaderArtifactCache(store)).compileAsync(REQUEST, Runnable::run);
        var uncached = new ShaderTranslationCache(compiler, null).compileAsync(REQUEST, Runnable::run);
        pending.completeExceptionally(new IllegalStateException("worker terminated"));
        assertTrue(cached.isFailed());
        assertTrue(uncached.isFailed());
        assertTrue(store.records.isEmpty());
    }

    private static RuntimeShaderCompiler asynchronousCompiler(FdxFuture<RuntimeShaderCompileResult> pending) {
        return new RuntimeShaderCompiler() {
            @Override public String cacheIdentity() { return "async-compiler"; }
            @Override public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
                throw new AssertionError("Synchronous compile must not run on an async cache miss");
            }
            @Override public FdxFuture<RuntimeShaderCompileResult> compileAsync(RuntimeShaderCompileRequest request,
                    java.util.function.Consumer<Runnable> execute) { return pending; }
        };
    }

    @Test void pendingReadReleasesExecutorAndFreshAdapterRestoresReflectionWithoutCompiling() {
        Store store = new Store();
        Compiler compiler = new Compiler();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        store.pending = FdxFuture.pending();
        var first = new ShaderTranslationCache(compiler, cache).compileAsync(REQUEST, work::add);
        assertTrue(work.isEmpty());
        assertFalse(first.isDone());
        assertEquals(0, compiler.calls);
        store.pending.complete(null);
        drain(work);
        assertTrue(first.get().success());
        assertEquals(1, compiler.calls);
        assertEquals(1, store.records.size());
        store.pending = null;
        Compiler nextProcess = new Compiler();
        var replay = new ShaderTranslationCache(nextProcess, new ShaderArtifactCache(store)).compileAsync(REQUEST, work::add);
        drain(work);
        assertEquals(0, nextProcess.calls);
        assertArrayEquals(first.get().reflection().bytes(), replay.get().reflection().bytes());
    }

    @Test void changesToCompilerSourceOptionsAndTargetInvalidateIndependently() {
        Store store = new Store(); Compiler compiler = new Compiler();
        ShaderTranslationCache cache = new ShaderTranslationCache(compiler, new ShaderArtifactCache(store));
        cache.compileAsync(REQUEST, Runnable::run).get();
        cache.compileAsync(REQUEST, Runnable::run).get();
        assertEquals(1, compiler.calls);
        compiler.identity = "compiler-2";
        cache.compileAsync(REQUEST, Runnable::run).get();
        cache.compileAsync(RuntimeShaderCompileRequest.builder("changed", REQUEST.target()).build(), Runnable::run).get();
        cache.compileAsync(RuntimeShaderCompileRequest.builder(REQUEST.source(), REQUEST.target()).glslProfile("450").build(), Runnable::run).get();
        cache.compileAsync(RuntimeShaderCompileRequest.builder(REQUEST.source(), RuntimeShaderCompileTarget.WEBGPU_WGSL).build(), Runnable::run).get();
        assertEquals(5, compiler.calls);
    }

    @Test void corruptEnvelopeAndInvalidReflectedPayloadRecoverThroughCompiler() {
        Store store = new Store(); Compiler compiler = new Compiler(); ShaderArtifactCache artifacts = new ShaderArtifactCache(store);
        ShaderTranslationCache cache = new ShaderTranslationCache(compiler, artifacts);
        cache.compileAsync(REQUEST, Runnable::run).get();
        String digest = store.records.keySet().iterator().next();
        store.records.get(digest)[137] ^= 1;
        assertTrue(cache.compileAsync(REQUEST, Runnable::run).get().success());
        artifacts.writeAsync(new ShaderCacheKey(ShaderCacheLayer.SOURCE, digest), new byte[]{'F','D','X','R'}).get();
        assertTrue(cache.compileAsync(REQUEST, Runnable::run).get().success());
        assertEquals(3, compiler.calls);
        assertEquals(2, artifacts.metrics(ShaderCacheLayer.SOURCE).invalidEntries());
    }

    @Test void unknownIdentityDisabledStorageAndExecutorShutdownDoNotWait() {
        Store store = new Store(); Compiler compiler = new Compiler(); compiler.identity = null;
        ShaderTranslationCache cache = new ShaderTranslationCache(compiler, new ShaderArtifactCache(store));
        assertTrue(cache.compileAsync(REQUEST, Runnable::run).get().success());
        assertTrue(store.records.isEmpty());
        assertTrue(new ShaderTranslationCache(compiler, null).compileAsync(REQUEST, Runnable::run).get().success());
        var rejected = cache.compileAsync(REQUEST, ignored -> { throw new RejectedExecutionException("closed"); });
        assertTrue(rejected.isDone());
        assertThrows(RuntimeException.class, rejected::get);
        assertEquals(2, compiler.calls);
    }

    private static void drain(ArrayDeque<Runnable> work) { while (!work.isEmpty()) work.remove().run(); }
    private static final class Compiler implements RuntimeShaderCompiler {
        int calls; String identity = "compiler-1";
        @Override public String cacheIdentity() { return identity; }
        @Override public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
            calls++;
            return RuntimeShaderCompileResult.text(request.source(), ShaderReflectionDecoderTest.runtimeFixture());
        }
    }
    private static final class Store implements ShaderCacheStore {
        final Map<String, byte[]> records = new HashMap<>(); FdxFuture<byte[]> pending;
        @Override public FdxFuture<byte[]> readAsync(String key) { return pending != null ? pending : FdxFuture.completed(records.get(key)); }
        @Override public FdxFuture<Void> writeAsync(String key, byte[] bytes) { records.put(key, bytes); return FdxFuture.completed(null); }
        @Override public FdxFuture<Void> removeAsync(String key) { records.remove(key); return FdxFuture.completed(null); }
    }
}
