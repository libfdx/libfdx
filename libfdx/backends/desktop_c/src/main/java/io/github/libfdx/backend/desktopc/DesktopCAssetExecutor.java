package io.github.libfdx.backend.desktopc;

import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.core.FdxException;
import java.util.ArrayDeque;

/**
 * Bounded cooperative Java workers for the C runtime's single event-loop thread.
 * Queue operations never suspend, so they do not need a contended Java monitor.
 * Accepted tasks run on workers; saturation never runs work on the caller.
 * Dispose asset managers before this executor. Disposal rejects new work and
 * drains accepted tasks without blocking the application thread.
 */
public final class DesktopCAssetExecutor implements AssetExecutor {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final int capacity;
    private volatile boolean disposed;

    public DesktopCAssetExecutor(int workers, int queuedTasks) {
        if (workers < 1 || queuedTasks < 1) {
            throw new FdxException("Asset worker and queue capacities must be positive");
        }
        capacity = queuedTasks;
        try {
            for (int index = 0; index < workers; index++) {
                Thread worker = new Thread(this::work, "libfdx-native-worker-" + index);
                worker.setDaemon(true);
                worker.start();
            }
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    @Override
    public boolean submit(Runnable task) {
        if (task == null) throw new FdxException("Asset task cannot be null");
        if (disposed) throw new FdxException("Asset executor is disposed");
        if (queue.size() == capacity) return false;
        queue.addLast(task);
        return true;
    }

    private void work() {
        while (true) {
            if (queue.isEmpty()) {
                if (disposed) return;
                try { Thread.sleep(1); }
                catch (InterruptedException ignored) { }
                continue;
            }
            Runnable task = queue.removeFirst();
            try {
                task.run();
            } catch (Throwable failure) {
                failure.printStackTrace();
            }
            Thread.yield();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
