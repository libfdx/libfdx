package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GLProgramCacheTest {
    @Test void freshLoadingOperationRestoresWithoutCompilingOrRelinking() {
        Store store = populated();
        Run warm = new Run(store); warm.finishLoading();
        assertEquals(1, warm.f.gl.binaryRestores);
        assertEquals(0, warm.f.gl.compiles);
        assertEquals(0, warm.f.gl.links);
        assertEquals(0, warm.f.gl.binaryExports);
        assertEquals(1, warm.cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).hits());
        assertEquals(0, warm.cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).pipelineFeedbacks());
    }

    @Test void ordinaryRuntimePreparationNeverImportsOrExportsEvenOnCacheHit() throws Exception {
        Run runtime = new Run(populated()); runtime.f.loading = false;
        runtime.op.prepareAsync(); runtime.f.gl.runWorkers();
        assertFalse(runtime.op.isDone());
        assertTrue(runtime.op.isDone()); runtime.disposeResult();
        assertEquals(2, runtime.f.gl.compiles);
        assertEquals(0, runtime.f.gl.binaryRestores);
        assertEquals(0, runtime.f.gl.binaryExports);
        assertEquals(0, runtime.f.gl.binaryHints);
    }

    @Test void switchingToOrdinaryPollingCannotFinishABinaryImport() {
        Run run = new Run(populated());
        run.op.prepareLoading(); run.op.advanceLoading();
        assertEquals(1, run.f.gl.binaryRestores);
        for (int i = 0; i < 12; i++) assertFalse(run.op.isDone());
        assertEquals(0, run.f.gl.linkStatuses);
        run.op.advanceLoading(); assertTrue(run.op.isDone()); run.disposeResult();
    }

    @Test void loadingCompilationFinishesAndExportsOnlyInLoading() {
        Run run = new Run(new Store());
        run.op.prepareLoading(); run.op.advanceLoading();
        assertEquals(1, run.f.gl.binaryHints);
        for (int i = 0; i < 12; i++) assertFalse(run.op.isDone());
        assertEquals(0, run.f.gl.binaryExports);
        run.op.advanceLoading(); assertTrue(run.op.isDone()); run.disposeResult();
        assertEquals(1, run.f.gl.binaryExports);
    }

    @Test void rejectedBinaryRebuildsExistingGlslOnceAndReplacesOnlyItsRecord() {
        Store store = populated(); store.entries.put("unrelated", new byte[] {9});
        Run run = new Run(store); run.f.gl.binaryAccepted = false; run.finishLoading();
        assertEquals(1, run.f.gl.binaryRestores);
        assertEquals(2, run.f.gl.compiles); assertEquals(1, run.f.gl.links);
        assertEquals(1, run.f.gl.binaryExports);
        assertEquals(2, run.f.gl.deletedPrograms);
        assertEquals(1, run.cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).invalidEntries());
        assertArrayEquals(new byte[] {9}, store.entries.get("unrelated"));
        new Run(store).finishLoading();
    }

    @Test void linkStatusRejectionAlsoRetriesFromGlsl() {
        Run run = new Run(populated());
        run.op.prepareLoading(); run.op.advanceLoading();
        run.f.gl.linkValid = false;
        run.op.advanceLoading();
        run.f.gl.linkValid = true;
        for (int i = 0; i < 4; i++) run.op.advanceLoading();
        assertTrue(run.op.isDone()); run.disposeResult();
        assertEquals(1, run.f.gl.binaryRestores); assertEquals(1, run.f.gl.links);
    }

    @Test void corruptOrIncompatibleIdentityIsAMiss() {
        Store store = populated();
        Run changed = new Run(store, "other-driver"); changed.finishLoading();
        assertEquals(0, changed.f.gl.binaryRestores); assertEquals(2, changed.f.gl.compiles);
        store.entries.values().forEach(bytes -> bytes[bytes.length - 1] ^= 1);
        Run corrupt = new Run(store); corrupt.finishLoading();
        assertEquals(0, corrupt.f.gl.binaryRestores); assertEquals(2, corrupt.f.gl.compiles);
        assertEquals(1, corrupt.cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).invalidEntries());
    }

    @Test void cancelledPendingReadAndLateCompletionNeverUseDestroyedContext() throws Exception {
        for (boolean lost : new boolean[] {false, true}) {
            Store store = populated(); store.held = FdxFuture.pending();
            Run run = new Run(store); run.f.loading = false;
            run.op.prepareAsync(); run.f.gl.runWorkers();
            assertFalse(run.op.isDone());
            run.op.close(lost); run.f.gl.lost = lost; run.f.attachment.dispose();
            store.held.complete(store.entries.values().iterator().next().clone());
            assertTrue(run.op.isDone());
            assertThrows(CancellationException.class, run.op::finish); run.op.dispose();
            assertEquals(0, run.f.gl.binaryRestores); assertEquals(0, run.f.gl.compiles);
        }
    }

    @Test void cancellationAfterImportDeletesUnpublishedProgram() {
        Run run = new Run(populated());
        run.op.prepareLoading(); run.op.advanceLoading(); run.op.cancel();
        assertTrue(run.op.isDone()); assertThrows(CancellationException.class, run.op::finish);
        run.op.dispose(); run.f.attachment.dispose();
        assertEquals(1, run.f.gl.deletedPrograms);
    }

    @Test void exportAndStorageFailuresDoNotInvalidatePreparedResult() {
        Run export = new Run(new Store()); export.f.gl.exportFails = true; export.finishLoading();
        assertEquals(0, export.store.entries.size());
        Store store = new Store(); store.failWrites = true;
        Run write = new Run(store); write.finishLoading();
        assertEquals(1, write.cache.metrics(ShaderCacheLayer.DRIVER_PIPELINE).storageFailures());
    }

    private static Store populated() {
        Store store = new Store(); Run cold = new Run(store); cold.finishLoading();
        assertEquals(1, cold.f.gl.binaryExports); assertEquals(1, store.entries.size());
        return store;
    }

    private static final class Run {
        final GLPreparationTest.Fixture f = new GLPreparationTest.Fixture();
        final Store store;
        final ShaderArtifactCache cache;
        final GLPreparationOperation op;
        Run(Store store) { this(store, "test-driver"); }
        Run(Store store, String identity) {
            this.store = store; cache = new ShaderArtifactCache(store);
            f.loading = true; f.gl.linkComplete = true;
            op = new GLPreparationOperation((GLGraphicsDevice) f.attachment.device(), f.attachment, f.gl.api,
                    f.provider, f.attachment.resourceDomain(), f.request, f.compilers, f.compilers,
                    new GLProgramCache(cache, identity, f.gl.api));
        }
        void finishLoading() {
            op.prepareLoading();
            for (int i = 0; i < 8; i++) {
                op.advanceLoading();
                // The service polls immediately after every loading advance.
                if (op.isDone()) break;
            }
            assertTrue(op.isDone()); disposeResult();
        }
        void disposeResult() {
            var result = op.finish(); op.dispose(); result.dispose(); f.attachment.dispose();
        }
    }

    private static final class Store implements ShaderCacheStore {
        final Map<String, byte[]> entries = new HashMap<>();
        FdxFuture<byte[]> held;
        boolean failWrites;
        @Override public FdxFuture<byte[]> readAsync(String key) {
            return held != null ? held : FdxFuture.completed(entries.containsKey(key) ? entries.get(key).clone() : null);
        }
        @Override public FdxFuture<Void> writeAsync(String key, byte[] bytes) {
            if (failWrites) return FdxFuture.failed(new IllegalStateException("Unavailable test storage"));
            entries.put(key, bytes.clone()); return FdxFuture.completed(null);
        }
        @Override public FdxFuture<Void> removeAsync(String key) { entries.remove(key); return FdxFuture.completed(null); }
    }
}
