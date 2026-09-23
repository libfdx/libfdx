package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.*;
import io.github.libfdx.files.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Supplier;

/** Test-owned timings of loader task bodies; worker and application-thread stages are reported separately. */
public final class AssetLoadingMetrics {
    private final ArrayList<Stage> stages = new ArrayList<>();

    public <T> AssetLoader<T> measure(AssetLoader<T> loader) {
        return new AssetLoader<>() {
            @Override
            public Class<T> type() { return loader.type(); }
            @Override
            public FdxFuture<T> load(AssetLoadContext context, AssetDescriptor<T> descriptor) {
                return loader.load(new Context(context, descriptor.path()), descriptor);
            }
        };
    }

    public synchronized void report(Logger logger) {
        stages.sort(Comparator.comparingLong((Stage stage) -> stage.maxNanos).reversed());
        for (int i = 0; i < Math.min(12, stages.size()); i++) {
            Stage stage = stages.get(i);
            logger.info("ASSET_STAGE path=" + stage.path + " stage=" + stage.name + " calls=" + stage.calls
                    + " maxMs=" + stage.maxNanos / 1e6 + " totalMs=" + stage.totalNanos / 1e6);
        }
    }

    private final class Context implements AssetLoadContext {
        private final AssetLoadContext delegate;
        private final String path;
        private int sequence;
        Context(AssetLoadContext delegate, String path) { this.delegate = delegate; this.path = path; }
        @Override
        public FileSystem files() { return delegate.files(); }
        @Override
        public <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor) { return delegate.dependency(descriptor); }
        @Override
        public <T extends Disposable> T preparationResource(Class<T> type, Supplier<? extends T> factory) {
            return delegate.preparationResource(type, factory);
        }
        @Override
        public <T> FdxFuture<T> completeOnUpdate(FdxTask<T> task) { return delegate.completeOnUpdate(wrap("finalize", task)); }
        @Override
        public <T> FdxFuture<T> async(FdxTask<T> task) { return delegate.async(wrap("cpu", task)); }
        @Override
        public FdxFuture<Void> asyncSteps(FdxTask<Boolean> task) { return delegate.asyncSteps(wrap("cpuSteps", task)); }
        @Override
        public <T> FdxFuture<T> asyncFuture(FdxTask<FdxFuture<T>> task) { return delegate.asyncFuture(wrap("asyncStart", task)); }
        @Override
        public FdxFuture<byte[]> readBytes(FileHandle file) { return delegate.readBytes(file); }
        private <T> FdxTask<T> wrap(String name, FdxTask<T> task) {
            Stage stage = new Stage(path, name + "-" + sequence++);
            synchronized (AssetLoadingMetrics.this) { stages.add(stage); }
            return () -> {
                long start = System.nanoTime();
                try { return task.run(); }
                finally {
                    long elapsed = System.nanoTime() - start;
                    synchronized (AssetLoadingMetrics.this) {
                        stage.calls++; stage.totalNanos += elapsed;
                        stage.maxNanos = Math.max(stage.maxNanos, elapsed);
                    }
                }
            };
        }
    }

    private static final class Stage {
        final String path, name;
        long calls, totalNanos, maxNanos;
        Stage(String path, String name) { this.path = path; this.name = name; }
    }
}
