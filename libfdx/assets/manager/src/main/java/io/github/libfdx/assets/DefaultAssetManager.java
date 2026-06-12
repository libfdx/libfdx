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

/**
 * Manages default asset resources.
 *
 * @author xpenatan
 */
public final class DefaultAssetManager implements AssetManager {
    private final Map<Class<?>, AssetLoader<?>> loaders = new LinkedHashMap<Class<?>, AssetLoader<?>>();
    private final Map<String, DefaultAssetHandle<?>> handles = new LinkedHashMap<String, DefaultAssetHandle<?>>();
    private final Queue<Runnable> updateTasks = new ArrayDeque<Runnable>();
    private final FileSystem files;
    private boolean disposed;

    /**
     * Creates a default asset manager.
     *
     * @param files the files
     */
    public DefaultAssetManager(FileSystem files) {
        if (files == null) {
            throw new FdxException("FileSystem cannot be null");
        }
        this.files = files;
    }

    /**
     * Loads the requested resource.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
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

    /**
     * Starts loading the requested resource asynchronously.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor) {
        return load(descriptor).future();
    }

    /**
     * Updates this instance and reports whether work remains.
     *
     * @return true if more work remains; false otherwise
     */
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

    /**
     * Finishes pending load work before returning.
     */
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

    /**
     * Returns the loaded asset at the given path.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the loaded asset
     */
    @Override
    public synchronized <T> T get(String path, Class<T> type) {
        T asset = find(path, type);
        if (asset == null) {
            throw new FdxException("Asset is not loaded: " + path + " as " + type.getName());
        }
        return asset;
    }

    /**
     * Finds a matching value.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the matching value, or null if none is available
     */
    @Override
    public synchronized <T> T find(String path, Class<T> type) {
        DefaultAssetHandle<?> handle = handles.get(key(path, type));
        if (handle == null || !handle.isLoaded()) {
            return null;
        }
        Object asset = handle.asset();
        return type.isInstance(asset) ? type.cast(asset) : null;
    }

    /**
     * Unloads the resource at the given path.
     *
     * @param path the asset or file path
     */
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

    /**
     * Registers an asset loader for a Java type.
     *
     * @param type the expected Java type
     * @param loader the loader to register
     */
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

    /**
     * Releases resources held by this instance.
     */
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

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
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

    /**
     * Provides the default implementation of an asset load context.
     *
     * @author xpenatan
     */
    private final class DefaultAssetLoadContext implements AssetLoadContext {
        /**
         * Returns the files.
         *
         * @return the files
         */
        @Override
        public FileSystem files() {
            return files;
        }

        /**
         * Runs the dependency step.
         *
         * @param <T> the value type
         * @param descriptor the descriptor
         * @return the dependency
         */
        @Override
        public <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor) {
            return load(descriptor).future();
        }

        /**
         * Runs the complete on update step.
         *
         * @param <T> the value type
         * @param task the task
         * @return the complete on update
         */
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

    /**
     * Provides the default implementation of an asset handle.
     *
     * @param <T> the value type
     *
     * @author xpenatan
     */
    private static final class DefaultAssetHandle<T> implements AssetHandle<T> {
        private final AssetDescriptor<T> descriptor;
        private final FdxFuture<T> future = FdxFuture.pending();
        private volatile AssetStatus status = AssetStatus.QUEUED;
        private volatile T asset;

        DefaultAssetHandle(AssetDescriptor<T> descriptor) {
            this.descriptor = descriptor;
        }

        /**
         * Returns the descriptor.
         *
         * @return the descriptor
         */
        @Override
        public AssetDescriptor<T> descriptor() {
            return descriptor;
        }

        /**
         * Returns the status.
         *
         * @return the status
         */
        @Override
        public AssetStatus status() {
            return status;
        }

        /**
         * Returns whether loaded is enabled or true.
         *
         * @return true if loaded is enabled or true; false otherwise
         */
        @Override
        public boolean isLoaded() {
            return status == AssetStatus.LOADED;
        }

        /**
         * Returns the asset.
         *
         * @return the asset
         */
        @Override
        public T asset() {
            return asset;
        }

        /**
         * Returns the future.
         *
         * @return the future
         */
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
