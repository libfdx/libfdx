package io.github.libfdx.backend.android;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AndroidShaderPreparationExecutorTest {
    @Test void shutdownReturnsWhileNativeWorkDrainsAndNeverRunsWorkOnTheCaller() throws Exception {
        AndroidShaderPreparationExecutor executor = new AndroidShaderPreparationExecutor(1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), drained = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        executor.execute(() -> {
            worker.set(Thread.currentThread()); entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        });
        executor.execute(drained::countDown);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), worker.get());
            assertTimeoutPreemptively(Duration.ofSeconds(1), executor::dispose);
            assertEquals(1, drained.getCount(), "Accepted work must not be moved onto the closing thread");
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> fail("Rejected work ran")));
        } finally { release.countDown(); executor.dispose(); }
        assertTrue(drained.await(5, TimeUnit.SECONDS), "Accepted work must finish after shutdown");
    }
}
