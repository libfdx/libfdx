package io.github.libfdx.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
