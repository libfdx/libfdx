package io.github.libfdx.backend.desktopcpp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.core.FdxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class DesktopCppAssetExecutorTest {
    @Test
    void saturationRejectsAndDisposalDrainsAcceptedWorkOnWorkers() throws Exception {
        var executor = new DesktopCppAssetExecutor(1, 1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var drained = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        try {
            assertTrue(
                    executor.submit(
                            () -> {
                                worker.set(Thread.currentThread());
                                started.countDown();
                                try {
                                    release.await();
                                } catch (InterruptedException error) {
                                    throw new AssertionError(error);
                                }
                            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(executor.submit(drained::countDown));
            assertFalse(executor.submit(() -> fail("Rejected task ran")));
            assertNotSame(Thread.currentThread(), worker.get());
            executor.dispose();
            assertTrue(executor.isDisposed());
            assertThrows(FdxException.class, () -> executor.submit(() -> {}));
            release.countDown();
            assertTrue(drained.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.dispose();
        }
    }
}
