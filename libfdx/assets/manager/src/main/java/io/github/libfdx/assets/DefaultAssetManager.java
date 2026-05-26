package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileSystem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

public final class DefaultAssetManager implements AssetManager {
    private final Map<Class<?>, AssetLoader<?>> loaders = new LinkedHashMap<Class<?>, AssetLoader<?>>();
    private final Map<String, DefaultAssetHandle<?>> handles = new LinkedHashMap<String, DefaultAssetHandle<?>>();
    private final Queue<Runnable> updateTasks = new ArrayDeque<Runnable>();
    private final FileSystem files;
    private boolean disposed;

    public DefaultAssetManager(FileSystem files) {
        if (files == null) {
            throw new FdxException("FileSystem cannot be null");
        }
        this.files = files;
    }

    @Override
    public synchronized <T> AssetHandle<T> load(AssetDescriptor<T> descriptor) {
        ensureNotDisposed();
        String key = key(descriptor.path(), descriptor.type());
        DefaultAssetHandle<?> existing = handles.get(key);
        if (existing != null) {
            return cast(existing);
        }
        DefaultAssetHandle<T> handle = new DefaultAssetHandle<T>(descriptor);
        handles.put(key, handle);
        startLoad(handle);
        return handle;
    }

    @Override
    public <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor) {
        return load(descriptor).future();
    }

    @Override
    public boolean update() {
        ensureNotDisposed();
        drainUpdateTasks();
        synchronized (this) {
            for (DefaultAssetHandle<?> handle : handles.values()) {
                AssetStatus status = handle.status();
                if (status == AssetStatus.QUEUED || status == AssetStatus.LOADING) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void finishLoading() {
        ensureNotDisposed();
        while (!update()) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new FdxException("Interrupted while waiting for assets to load", error);
            }
        }
    }

    @Override
    public synchronized <T> T get(String path, Class<T> type) {
        T asset = find(path, type);
        if (asset == null) {
            throw new FdxException("Asset is not loaded: " + path + " as " + type.getName());
        }
        return asset;
    }

    @Override
    public synchronized <T> T find(String path, Class<T> type) {
        DefaultAssetHandle<?> handle = handles.get(key(path, type));
        if (handle == null || !handle.isLoaded()) {
            return null;
        }
        Object asset = handle.asset();
        return type.isInstance(asset) ? type.cast(asset) : null;
    }

    @Override
    public synchronized void unload(String path) {
        Iterator<Map.Entry<String, DefaultAssetHandle<?>>> iterator = handles.entrySet().iterator();
        while (iterator.hasNext()) {
            DefaultAssetHandle<?> handle = iterator.next().getValue();
            if (handle.descriptor().path().equals(path)) {
                iterator.remove();
                Object asset = handle.asset();
                if (asset instanceof Disposable) {
                    ((Disposable) asset).dispose();
                }
                handle.unload();
            }
        }
    }

    @Override
    public synchronized void registerLoader(Class<?> type, AssetLoader<?> loader) {
        if (type == null) {
            throw new FdxException("Asset loader type cannot be null");
        }
        if (loader == null) {
            throw new FdxException("Asset loader cannot be null");
        }
        loaders.put(type, loader);
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (DefaultAssetHandle<?> handle : handles.values()) {
            Object asset = handle.asset();
            if (asset instanceof Disposable) {
                ((Disposable) asset).dispose();
            }
            handle.unload();
        }
        handles.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private <T> void startLoad(final DefaultAssetHandle<T> handle) {
        final AssetLoader<T> loader = loader(handle.descriptor().type());
        if (loader == null) {
            handle.fail(new FdxException("No asset loader registered for " + handle.descriptor().type().getName()));
            return;
        }
        handle.loading();
        try {
            loader.load(new DefaultAssetLoadContext(), handle.descriptor())
                    .onSuccess(handle::complete)
                    .onFailure(handle::fail);
        } catch (Throwable error) {
            handle.fail(error);
        }
    }

    private void drainUpdateTasks() {
        while (true) {
            Runnable task;
            synchronized (updateTasks) {
                task = updateTasks.poll();
            }
            if (task == null) {
                return;
            }
            task.run();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> AssetLoader<T> loader(Class<T> type) {
        return (AssetLoader<T>) loaders.get(type);
    }

    private String key(String path, Class<?> type) {
        return (path != null ? path.replace('\\', '/') : "") + "|" + (type != null ? type.getName() : "");
    }

    @SuppressWarnings("unchecked")
    private <T> AssetHandle<T> cast(DefaultAssetHandle<?> handle) {
        return (AssetHandle<T>) handle;
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("AssetManager is disposed");
        }
    }

    private final class DefaultAssetLoadContext implements AssetLoadContext {
        @Override
        public FileSystem files() {
            return files;
        }

        @Override
        public <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor) {
            return load(descriptor).future();
        }

        @Override
        public <T> FdxFuture<T> completeOnUpdate(final FdxTask<T> task) {
            final FdxFuture<T> future = FdxFuture.pending();
            synchronized (updateTasks) {
                updateTasks.add(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            future.complete(task.run());
                        } catch (Throwable error) {
                            future.completeExceptionally(error);
                        }
                    }
                });
            }
            return future;
        }
    }

    private static final class DefaultAssetHandle<T> implements AssetHandle<T> {
        private final AssetDescriptor<T> descriptor;
        private final FdxFuture<T> future = FdxFuture.pending();
        private volatile AssetStatus status = AssetStatus.QUEUED;
        private volatile T asset;

        DefaultAssetHandle(AssetDescriptor<T> descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public AssetDescriptor<T> descriptor() {
            return descriptor;
        }

        @Override
        public AssetStatus status() {
            return status;
        }

        @Override
        public boolean isLoaded() {
            return status == AssetStatus.LOADED;
        }

        @Override
        public T asset() {
            return asset;
        }

        @Override
        public FdxFuture<T> future() {
            return future;
        }

        void loading() {
            status = AssetStatus.LOADING;
        }

        void complete(T asset) {
            this.asset = asset;
            status = AssetStatus.LOADED;
            future.complete(asset);
        }

        void fail(Throwable error) {
            status = AssetStatus.FAILED;
            future.completeExceptionally(error != null ? error : new FdxException("Asset load failed"));
        }

        void unload() {
            status = AssetStatus.UNLOADED;
            asset = null;
        }
    }
}
