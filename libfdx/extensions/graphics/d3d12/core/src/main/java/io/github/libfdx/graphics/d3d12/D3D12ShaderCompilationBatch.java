package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Bounded startup work. Every native compiler call finishes before results or errors escape. */
final class D3D12ShaderCompilationBatch {
    private D3D12ShaderCompilationBatch() { }

    static <T> List<T> compile(List<? extends Callable<T>> jobs, Consumer<T> release) {
        return compile(jobs, release, Executors.defaultThreadFactory());
    }

    static <T> List<T> compile(List<? extends Callable<T>> jobs, Consumer<T> release,
            java.util.concurrent.ThreadFactory threads) {
        if (jobs.isEmpty()) return List.of();
        var tasks = new ArrayList<Task<T>>(jobs.size());
        for (Callable<T> job : jobs) tasks.add(new Task<>(job));
        try {
            int workers = Math.min(jobs.size(), Math.min(8, Runtime.getRuntime().availableProcessors()));
            // Closing joins even when interrupted: cancelling a Future cannot stop a native call
            // and could otherwise lose its returned COM reference.
            try (var executor = Executors.newFixedThreadPool(workers, threads)) {
                for (Task<T> task : tasks) executor.execute(task);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new FdxException("Interrupted while compiling Direct3D 12 shader batch");
            }
            for (Task<T> task : tasks) if (task.failure != null) {
                if (task.failure instanceof Error error) throw error;
                if (task.failure instanceof RuntimeException error) throw error;
                throw new FdxException("Could not compile Direct3D 12 shader batch", task.failure);
            }
            var results = new ArrayList<T>(tasks.size());
            for (Task<T> task : tasks) results.add(task.result);
            return results;
        } catch (RuntimeException | Error failure) {
            for (Task<T> task : tasks) if (task.result != null) {
                try { release.accept(task.result); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static final class Task<T> implements Runnable {
        private final Callable<T> job;
        private volatile T result;
        private volatile Throwable failure;

        private Task(Callable<T> job) { this.job = job; }

        @Override
        public void run() {
            try { result = job.call(); }
            catch (Throwable error) { failure = error; }
        }
    }
}
