package io.github.libfdx.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies portable future completion and callback behavior.
 *
 * @author xpenatan
 */
final class FdxFutureTest {
    @Test
    void successDispatchPreservesOrderAndContinuesAfterCallbackFailures() {
        FdxFuture<String> future = FdxFuture.pending();
        List<String> order = new ArrayList<String>();
        RuntimeException firstFailure = new IllegalStateException("first callback");
        RuntimeException secondFailure = new IllegalArgumentException("second callback");

        future.onSuccess(value -> {
            order.add("first:" + value);
            throw firstFailure;
        });
        future.onSuccess(value -> {
            order.add("second:" + value);
            future.onSuccess(lateValue -> order.add("late:" + lateValue));
            throw secondFailure;
        });
        future.onSuccess(value -> order.add("third:" + value));

        RuntimeException reported = assertThrows(RuntimeException.class, () -> future.complete("ready"));

        assertSame(firstFailure, reported);
        assertEquals(1, reported.getSuppressed().length);
        assertSame(secondFailure, reported.getSuppressed()[0]);
        assertEquals(List.of("first:ready", "second:ready", "third:ready", "late:ready"), order);
        assertTrue(future.isDone());
        assertFalse(future.isFailed());
        assertEquals("ready", future.get());

        future.complete("ignored");
        assertEquals("ready", future.join());
        assertEquals(4, order.size());
    }

    @Test
    void failureDispatchPreservesOriginalResultAndContinuesAfterCallbackFailure() {
        FdxFuture<String> future = FdxFuture.pending();
        List<String> order = new ArrayList<String>();
        RuntimeException resultFailure = new IllegalStateException("operation failed");
        RuntimeException callbackFailure = new IllegalArgumentException("failure callback");

        future.onFailure(error -> {
            assertSame(resultFailure, error);
            order.add("first");
            throw callbackFailure;
        });
        future.onFailure(error -> {
            assertSame(resultFailure, error);
            order.add("second");
        });
        future.onSuccess(value -> order.add("wrong path"));

        RuntimeException reported = assertThrows(RuntimeException.class,
                () -> future.completeExceptionally(resultFailure));

        assertSame(callbackFailure, reported);
        assertEquals(List.of("first", "second"), order);
        assertTrue(future.isDone());
        assertTrue(future.isFailed());
        assertSame(resultFailure, assertThrows(RuntimeException.class, future::get));

        future.complete("ignored");
        future.completeExceptionally(new IllegalStateException("ignored"));
        assertSame(resultFailure, assertThrows(RuntimeException.class, future::join));
    }

    @Test
    void callbacksRegisteredAfterCompletionRunOnceWithoutChangingTheResult() {
        FdxFuture<String> completed = FdxFuture.completed("ready");
        RuntimeException callbackFailure = new IllegalStateException("late callback");

        RuntimeException reported = assertThrows(RuntimeException.class,
                () -> completed.onSuccess(value -> {
                    throw callbackFailure;
                }));
        assertSame(callbackFailure, reported);
        assertEquals("ready", completed.get());

        int[] successCalls = { 0 };
        completed.onSuccess(value -> successCalls[0]++);
        completed.onFailure(error -> successCalls[0] += 100);
        assertEquals(1, successCalls[0]);

        RuntimeException resultFailure = new IllegalArgumentException("failed result");
        FdxFuture<String> failed = FdxFuture.failed(resultFailure);
        int[] failureCalls = { 0 };
        failed.onFailure(error -> {
            assertSame(resultFailure, error);
            failureCalls[0]++;
        });
        failed.onSuccess(value -> failureCalls[0] += 100);
        assertEquals(1, failureCalls[0]);
        assertSame(resultFailure, assertThrows(RuntimeException.class, failed::get));
    }

    @Test
    void successCallbackRegisteredFromAnotherThreadDuringDispatchRunsOnce() throws Exception {
        FdxFuture<String> future = FdxFuture.pending();
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        AtomicInteger lateCalls = new AtomicInteger();
        AtomicReference<Throwable> completionFailure = new AtomicReference<Throwable>();

        future.onSuccess(value -> {
            dispatchStarted.countDown();
            await(releaseDispatch);
        });

        Thread completionThread = new Thread(() -> {
            try {
                future.complete("ready");
            } catch (Throwable error) {
                completionFailure.set(error);
            }
        }, "fdx-future-success-completion");
        completionThread.start();

        assertTrue(dispatchStarted.await(5L, TimeUnit.SECONDS));
        future.onSuccess(value -> lateCalls.incrementAndGet());
        releaseDispatch.countDown();
        completionThread.join(5_000L);

        assertFalse(completionThread.isAlive());
        assertNull(completionFailure.get());
        assertEquals(1, lateCalls.get());
    }

    @Test
    void failureCallbackRegisteredFromAnotherThreadDuringDispatchRunsOnce() throws Exception {
        FdxFuture<String> future = FdxFuture.pending();
        RuntimeException resultFailure = new IllegalStateException("operation failed");
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        AtomicInteger lateCalls = new AtomicInteger();
        AtomicReference<Throwable> completionFailure = new AtomicReference<Throwable>();

        future.onFailure(error -> {
            dispatchStarted.countDown();
            await(releaseDispatch);
        });

        Thread completionThread = new Thread(() -> {
            try {
                future.completeExceptionally(resultFailure);
            } catch (Throwable error) {
                completionFailure.set(error);
            }
        }, "fdx-future-failure-completion");
        completionThread.start();

        assertTrue(dispatchStarted.await(5L, TimeUnit.SECONDS));
        future.onFailure(error -> {
            assertSame(resultFailure, error);
            lateCalls.incrementAndGet();
        });
        releaseDispatch.countDown();
        completionThread.join(5_000L);

        assertFalse(completionThread.isAlive());
        assertNull(completionFailure.get());
        assertEquals(1, lateCalls.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for callback dispatch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for callback dispatch", error);
        }
    }
}
