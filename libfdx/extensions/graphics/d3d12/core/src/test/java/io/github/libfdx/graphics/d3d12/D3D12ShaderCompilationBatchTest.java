package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class D3D12ShaderCompilationBatchTest {
    @Test
    void compilesConcurrentlyAndReturnsInputOrder() {
        int workers = Math.min(8, Runtime.getRuntime().availableProcessors());
        CountDownLatch started = new CountDownLatch(workers);
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int index = i;
            jobs.add(() -> {
                started.countDown();
                assertTrue(started.await(5, TimeUnit.SECONDS), "Independent compilation jobs must overlap");
                return index;
            });
        }
        List<Integer> result = D3D12ShaderCompilationBatch.compile(jobs, ignored -> fail("Released successful result"));
        for (int i = 0; i < workers; i++) assertEquals(i, result.get(i));
    }

    @Test
    void failedBatchJoinsAllJobsAndReleasesEverySuccessfulResult() {
        RuntimeException original = new IllegalArgumentException("shader diagnostic");
        RuntimeException cleanup = new IllegalStateException("release diagnostic");
        List<Integer> released = new ArrayList<>();
        List<Callable<Integer>> jobs = List.of(() -> 1, () -> { throw original; }, () -> 3);
        assertSame(original, assertThrows(RuntimeException.class, () ->
                D3D12ShaderCompilationBatch.compile(jobs, value -> {
                    released.add(value);
                    if (value == 1) throw cleanup;
                })));
        assertEquals(List.of(1, 3), released);
        assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
    }

    @Test
    void interruptionWaitsForNativeStyleWorkAndReleasesItsResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicBoolean released = new AtomicBoolean(), interrupted = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                D3D12ShaderCompilationBatch.compile(List.<Callable<Integer>>of(() -> {
                    started.countDown();
                    // Model a native compiler call which cannot be cancelled with Thread.interrupt.
                    boolean done = false;
                    while (!done) try { finish.await(); done = true; }
                    catch (InterruptedException ignored) { }
                    return 42;
                }), value -> released.set(value == 42));
            } catch (Throwable error) { failure.set(error); }
            finally { interrupted.set(Thread.currentThread().isInterrupted()); }
        });
        caller.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue(caller.isAlive(), "Must wait for native compilation before returning");
        } finally {
            finish.countDown();
            caller.join(5000);
        }
        assertFalse(caller.isAlive());
        assertInstanceOf(FdxException.class, failure.get());
        assertTrue(released.get());
        assertTrue(interrupted.get());
    }

    @Test
    void emptyBatchDoesNotCreateWork() {
        assertEquals(List.of(), D3D12ShaderCompilationBatch.compile(List.of(), ignored -> fail()));
    }
}
