package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxFuture;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPreparationTraceTest {
    @Test
    void transitionsAreMeasuredWithoutPollingAndSnapshotsStayImmutable() {
        AtomicLong clock = new AtomicLong(-100);
        ShaderPreparationTrace trace = new ShaderPreparationTrace(clock::get);
        clock.addAndGet(10); trace.enter(ShaderPreparationPhase.SOURCE);
        clock.addAndGet(20); trace.enter(ShaderPreparationPhase.TRANSLATION);
        clock.addAndGet(30); trace.enter(ShaderPreparationPhase.COMPILATION);
        clock.addAndGet(40); trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT);
        clock.addAndGet(50);
        var pending = snapshot(trace);
        clock.addAndGet(60); trace.enter(ShaderPreparationPhase.PUBLICATION);
        clock.addAndGet(70); trace.enter(ShaderPreparationPhase.COMPLETE);
        clock.addAndGet(80); trace.enter(ShaderPreparationPhase.PIPELINE);
        var done = snapshot(trace);
        assertEquals(10, done.phaseNanos(ShaderPreparationPhase.QUEUED));
        assertEquals(20, done.phaseNanos(ShaderPreparationPhase.SOURCE));
        assertEquals(30, done.phaseNanos(ShaderPreparationPhase.TRANSLATION));
        assertEquals(40, done.phaseNanos(ShaderPreparationPhase.COMPILATION));
        assertEquals(50, pending.phaseNanos(ShaderPreparationPhase.PUBLICATION_WAIT));
        assertEquals(110, done.phaseNanos(ShaderPreparationPhase.PUBLICATION_WAIT));
        assertEquals(70, done.phaseNanos(ShaderPreparationPhase.PUBLICATION));
        assertEquals(0, done.phaseNanos(ShaderPreparationPhase.PIPELINE));
        assertEquals(ShaderPreparationPhase.COMPLETE, trace.phase());
    }

    @Test
    void scopedWorkRestoresNestedContextEvenWhenItThrows() {
        ShaderPreparationTrace a = new ShaderPreparationTrace(), b = new ShaderPreparationTrace();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        a.executor(queue::add).accept(() -> {
            assertSame(a, ShaderPreparationTrace.current());
            assertThrows(IllegalStateException.class, () -> b.wrap(() -> {
                assertSame(b, ShaderPreparationTrace.current()); throw new IllegalStateException();
            }).run());
            assertSame(a, ShaderPreparationTrace.current());
        });
        assertNull(ShaderPreparationTrace.current());
        queue.remove().run();
        assertNull(ShaderPreparationTrace.current());
    }

    @Test
    void interleavedStorageCallbacksAndLateWritesBelongToInitiatingOperation() throws Exception {
        DelayedStore store = new DelayedStore();
        ShaderArtifactCache cache = new ShaderArtifactCache(store);
        ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.DXIL, "test");
        ShaderPreparationTrace a = new ShaderPreparationTrace(), b = new ShaderPreparationTrace();
        a.wrap(() -> {
            cache.readAsync(key).onSuccess(bytes -> {
                assertSame(a, ShaderPreparationTrace.current()); cache.rejected(key);
            });
            cache.writeAsync(key, new byte[]{1}); cache.compilerInvoked(key);
        }).run();
        b.wrap(() -> { cache.readAsync(key); cache.compilerInvoked(key); cache.compilerInvoked(key); }).run();
        a.enter(ShaderPreparationPhase.COMPLETE);
        var before = snapshot(a);
        Thread storage = new Thread(() -> b.wrap(() -> {
            store.readB.completeExceptionally(new IllegalStateException("unavailable"));
            store.readA.complete(store.record);
            store.write.complete(null);
        }).run());
        storage.start(); storage.join();
        var first = snapshot(a); var second = snapshot(b);
        assertEquals(0, before.cacheMetrics(key.layer()).writes());
        assertEquals(1, first.cacheMetrics(key.layer()).hits());
        assertEquals(1, first.cacheMetrics(key.layer()).writes());
        assertEquals(1, first.cacheMetrics(key.layer()).compilerInvocations());
        assertEquals(0, first.cacheMetrics(key.layer()).storageFailures());
        assertEquals(1, first.cacheMetrics(key.layer()).invalidEntries());
        assertEquals(0, second.cacheMetrics(key.layer()).invalidEntries());
        assertEquals(0, second.cacheMetrics(key.layer()).hits());
        assertEquals(0, second.cacheMetrics(key.layer()).writes());
        assertEquals(1, second.cacheMetrics(key.layer()).misses());
        assertEquals(1, second.cacheMetrics(key.layer()).storageFailures());
        assertEquals(2, second.cacheMetrics(key.layer()).compilerInvocations());
        assertTrue(first.cacheReadNanos(key.layer()) > 0);
        assertTrue(first.cacheWriteNanos(key.layer()) > 0);
        assertEquals(0, second.cacheWriteNanos(key.layer()));
        assertEquals(3, cache.metrics(key.layer()).compilerInvocations());
        assertNull(ShaderPreparationTrace.current());
    }

    private static ShaderPreparationTimings snapshot(ShaderPreparationTrace trace) {
        return trace.snapshot(0, 0, -1, -1, -1);
    }
    private static final class DelayedStore implements ShaderCacheStore {
        final FdxFuture<byte[]> readA = FdxFuture.pending(), readB = FdxFuture.pending();
        final FdxFuture<Void> write = FdxFuture.pending();
        int reads;
        byte[] record;
        @Override
        public FdxFuture<byte[]> readAsync(String key) { return reads++ == 0 ? readA : readB; }
        @Override
        public FdxFuture<Void> writeAsync(String key, byte[] value) { record = value; return write; }
        @Override
        public FdxFuture<Void> removeAsync(String key) { return FdxFuture.completed(null); }
    }
}
