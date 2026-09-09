package io.github.libfdx.backend.android;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded Java-created workers, already attached to the VM for JNI calls. Tasks own all JNI
 * inputs/results and never retain a JNIEnv or local reference across calls. Shutdown rejects
 * new work and drains accepted tasks without interrupting native calls or joining the owner. */
final class AndroidShaderPreparationExecutor implements Disposable {
    private final ThreadPoolExecutor executor;

    AndroidShaderPreparationExecutor(int workers) {
        if (workers < 1 || workers > 64) throw new FdxException("Shader preparation workers must be between 1 and 64");
        AtomicInteger next = new AtomicInteger();
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), task -> {
                    Thread thread = new Thread(task, "libfdx-shaders-" + next.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    void execute(Runnable task) { executor.execute(task); }
    @Override public void dispose() { executor.shutdown(); }
    @Override public boolean isDisposed() { return executor.isShutdown(); }
}
