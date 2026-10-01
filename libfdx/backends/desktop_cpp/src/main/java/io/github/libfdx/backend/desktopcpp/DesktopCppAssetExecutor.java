package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.core.FdxException;
import java.util.ArrayDeque;

/**
 * Bounded CPU workers for asset acquisition and shader source preparation.
 * Accepted tasks run on workers; saturation never runs work on the caller.
 * Dispose asset managers before this executor. Disposal rejects new work and
 * drains accepted tasks without blocking the application thread.
 */
public final class DesktopCppAssetExecutor implements AssetExecutor {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final int capacity;
    private volatile boolean disposed;

    public DesktopCppAssetExecutor(int workers, int queuedTasks) {
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
        synchronized (queue) {
            if (disposed) throw new FdxException("Asset executor is disposed");
            if (queue.size() == capacity) return false;
            queue.addLast(task);
            queue.notifyAll();
            return true;
        }
    }

    private void work() {
        while (true) {
            Runnable task;
            synchronized (queue) {
                while (queue.isEmpty() && !disposed) {
                    try {
                        queue.wait();
                    } catch (InterruptedException interrupted) {
                        // Accepted work remains owned by this pool until it has drained.
                    }
                }
                if (queue.isEmpty()) return;
                task = queue.removeFirst();
            }
            try {
                task.run();
            } catch (Throwable failure) {
                failure.printStackTrace();
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (queue) {
            disposed = true;
            queue.notifyAll();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
