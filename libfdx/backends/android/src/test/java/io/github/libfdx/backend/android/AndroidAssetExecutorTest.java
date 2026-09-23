package io.github.libfdx.backend.android;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
final class AndroidAssetExecutorTest {
    @Test
    void boundedQueueNeverRunsWorkOnTheCallerAndCloseDrainsAcceptedWork() throws Exception {
        AndroidAssetExecutor executor = new AndroidAssetExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        Thread applicationThread = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        try {
            assertTrue(executor.submit(() -> {
                worker.set(Thread.currentThread());
                started.countDown();
                await(release);
            }));
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertTrue(executor.submit(drained::countDown));
            assertFalse(executor.submit(() -> fail("Full queue ran rejected task")));
            assertNotSame(applicationThread, worker.get());
            executor.dispose();
            assertTrue(executor.isDisposed());
            assertThrows(FdxException.class, () -> executor.submit(() -> {}));
            release.countDown();
            assertTrue(drained.await(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.dispose();
        }
    }

    @Test
    void pendingFileDoesNotOccupyWorkerAndResultsAndFinalizersReturnToApplication() throws Exception {
        Thread applicationThread = Thread.currentThread();
        FdxFuture<byte[]> fileRead = FdxFuture.pending();
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch independentPrepared = new CountDownLatch(1);
        AtomicReference<Thread> acquisitionThread = new AtomicReference<>();
        FileHandle file = (FileHandle)Proxy.newProxyInstance(FileHandle.class.getClassLoader(),
                new Class<?>[] {FileHandle.class}, (proxy, method, args) -> {
                    acquisitionThread.set(Thread.currentThread());
                    acquisitionStarted.countDown();
                    return fileRead;
                });
        AndroidAssetExecutor executor = new AndroidAssetExecutor(1, 8);
        DefaultAssetManager manager = new DefaultAssetManager(files(), executor);
        manager.registerLoader(String.class, new AssetLoader<String>() {
            @Override public Class<String> type() { return String.class; }
            @Override public FdxFuture<String> load(AssetLoadContext context, AssetDescriptor<String> descriptor) {
                if (descriptor.path().equals("independent")) {
                    return context.async(() -> {
                        assertNotSame(applicationThread, Thread.currentThread());
                        independentPrepared.countDown();
                        return "independent";
                    });
                }
                FdxFuture<String> result = FdxFuture.pending();
                context.readBytes(file).onSuccess(bytes -> {
                    assertSame(applicationThread, Thread.currentThread());
                    context.completeOnUpdate(() -> {
                        assertSame(applicationThread, Thread.currentThread());
                        return "dependent";
                    }).onSuccess(result::complete).onFailure(result::completeExceptionally);
                }).onFailure(result::completeExceptionally);
                return result;
            }
        });
        try {
            AssetHandle<String> dependent = manager.load(AssetDescriptor.of("dependent", String.class));
            AssetHandle<String> independent = manager.load(AssetDescriptor.of("independent", String.class));
            dependent.future().onSuccess(value -> assertSame(applicationThread, Thread.currentThread()));
            manager.update();
            assertTrue(acquisitionStarted.await(3, TimeUnit.SECONDS));
            assertTrue(independentPrepared.await(3, TimeUnit.SECONDS));
            assertNotSame(applicationThread, acquisitionThread.get());
            pumpUntil(manager, independent);
            assertFalse(dependent.future().isDone());
            assertTrue(executor.submit(() -> fileRead.complete(new byte[0])));
            pumpUntil(manager, dependent);
            assertEquals("dependent", dependent.asset());
            AtomicReference<Throwable> wrongThread = new AtomicReference<>();
            CountDownLatch checked = new CountDownLatch(1);
            executor.submit(() -> {
                try { manager.update(); } catch (Throwable error) { wrongThread.set(error); }
                checked.countDown();
            });
            assertTrue(checked.await(3, TimeUnit.SECONDS));
            assertInstanceOf(FdxException.class, wrongThread.get());
        } finally {
            manager.dispose();
            assertFalse(executor.isDisposed());
            executor.dispose();
        }
    }

    @Test
    void lateOwnedCpuResultIsDisposedAfterManagerCancellation() throws Exception {
        AndroidAssetExecutor executor = new AndroidAssetExecutor(1, 1);
        DefaultAssetManager manager = new DefaultAssetManager(files(), executor);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch disposed = new CountDownLatch(1);
        Disposable value = new Disposable() {
            @Override public void dispose() { disposed.countDown(); }
            @Override public boolean isDisposed() { return disposed.getCount() == 0; }
        };
        manager.registerLoader(Disposable.class, new AssetLoader<Disposable>() {
            @Override public Class<Disposable> type() { return Disposable.class; }
            @Override public FdxFuture<Disposable> load(AssetLoadContext context, AssetDescriptor<Disposable> descriptor) {
                return context.async(() -> {
                    started.countDown();
                    await(release);
                    return value;
                });
            }
        });
        try {
            AssetHandle<Disposable> handle = manager.load(AssetDescriptor.of("cpu", Disposable.class));
            manager.update();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            manager.dispose();
            assertTrue(handle.future().isFailed());
            release.countDown();
            assertTrue(disposed.await(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            manager.dispose();
            executor.dispose();
        }
    }

    private static FileSystem files() {
        return (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(), new Class<?>[] {FileSystem.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException(); });
    }

    private static void pumpUntil(DefaultAssetManager manager, AssetHandle<?> handle) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!handle.future().isDone() && System.nanoTime() < deadline) {
            manager.update(1, Long.MAX_VALUE);
            Thread.sleep(1);
        }
        assertTrue(handle.isLoaded());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) { throw new AssertionError("Worker gate timed out"); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
