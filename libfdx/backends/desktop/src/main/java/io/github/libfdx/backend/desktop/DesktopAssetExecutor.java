package io.github.libfdx.backend.desktop;

import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.core.FdxException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded desktop workers for application-owned asset managers. Construct one
 * during application setup, pass it to DefaultAssetManager, then dispose managers
 * before this executor. Saturation applies backpressure; work never runs on the
 * submitting thread. Disposal rejects new work and lets accepted work finish;
 * it does not interrupt file/decoder operations or block the application thread.
 */
public final class DesktopAssetExecutor implements AssetExecutor {
    private final ThreadPoolExecutor executor;

    /**
     * @param workers maximum concurrent CPU/acquisition tasks, at least one
     * @param queuedTasks maximum waiting tasks, at least one
     */
    public DesktopAssetExecutor(int workers, int queuedTasks) {
        if (workers < 1 || queuedTasks < 1) {
            throw new FdxException("Asset worker and queue capacities must be positive");
        }
        AtomicInteger nextWorker = new AtomicInteger();
        executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queuedTasks), task -> {
                    Thread thread = new Thread(task, "libfdx-assets-" + nextWorker.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public boolean submit(Runnable task) {
        if (task == null) {
            throw new FdxException("Asset task cannot be null");
        }
        if (executor.isShutdown()) {
            throw new FdxException("Asset executor is disposed");
        }
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException error) {
            if (executor.isShutdown()) {
                throw new FdxException("Asset executor is disposed", error);
            }
            return false;
        }
    }

    @Override
    public void dispose() {
        executor.shutdown();
    }

    @Override
    public boolean isDisposed() {
        return executor.isShutdown();
    }
}
