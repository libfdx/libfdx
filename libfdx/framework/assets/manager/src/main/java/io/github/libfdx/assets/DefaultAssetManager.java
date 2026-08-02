package io.github.libfdx.assets;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.ObjectQueue;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileSystem;

/**
 * Manages default asset resources.
 *
 * @author xpenatan
 */
public final class DefaultAssetManager implements AssetManager {
    private final ObjectMap<Class<?>, AssetLoader<?>> loaders = new ObjectMap<Class<?>, AssetLoader<?>>();
    private final ObjectMap<String, DefaultAssetHandle<?>> handles =
            new ObjectMap<String, DefaultAssetHandle<?>>();
    private final Array<DefaultAssetHandle<?>> handleValues = new Array<DefaultAssetHandle<?>>();
    private final ObjectQueue<Runnable> updateTasks = new ObjectQueue<Runnable>();
    private final FileSystem files;
    private volatile boolean disposed;

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
        handleValues.add(handle);
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
            for (int i = 0; i < handleValues.size(); i++) {
                DefaultAssetHandle<?> handle = handleValues.get(i);
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
    public void unload(String path) {
        Array<DefaultAssetHandle<?>> unloadedHandles = new Array<DefaultAssetHandle<?>>();
        synchronized (this) {
            for (int i = handleValues.size() - 1; i >= 0; i--) {
                DefaultAssetHandle<?> handle = handleValues.get(i);
                if (handle.descriptor().path().equals(path)) {
                    handles.remove(key(handle.descriptor().path(), handle.descriptor().type()));
                    handleValues.removeIndex(i);
                    unloadedHandles.add(handle);
                }
            }
        }
        for (int i = 0; i < unloadedHandles.size(); i++) {
            DefaultAssetHandle<?> handle = unloadedHandles.get(i);
            handle.unload(new FdxException(
                    "Asset unloaded before loading completed: " + handle.descriptor().path()));
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
        ensureNotDisposed();
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
    public void dispose() {
        Array<DefaultAssetHandle<?>> disposedHandles;
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
            disposedHandles = new Array<DefaultAssetHandle<?>>(handleValues);
            handles.clear();
            handleValues.clear();
            loaders.clear();
        }
        synchronized (updateTasks) {
            updateTasks.clear();
        }
        for (int i = 0; i < disposedHandles.size(); i++) {
            DefaultAssetHandle<?> handle = disposedHandles.get(i);
            handle.unload(new FdxException(
                    "Asset manager disposed before loading completed: " + handle.descriptor().path()));
        }
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
                task = updateTasks.pollFirst();
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

    private static void disposeAsset(Object asset) {
        if (asset instanceof Disposable) {
            ((Disposable) asset).dispose();
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
            boolean rejected;
            synchronized (updateTasks) {
                rejected = disposed;
                if (!rejected) {
                    updateTasks.addLast(new Runnable() {
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
            }
            if (rejected) {
                future.completeExceptionally(new FdxException("AssetManager is disposed"));
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

        synchronized void loading() {
            if (status == AssetStatus.QUEUED) {
                status = AssetStatus.LOADING;
            }
        }

        void complete(T asset) {
            boolean accepted;
            synchronized (this) {
                accepted = status == AssetStatus.QUEUED || status == AssetStatus.LOADING;
                if (accepted) {
                    this.asset = asset;
                    status = AssetStatus.LOADED;
                }
            }
            if (!accepted) {
                disposeAsset(asset);
                return;
            }
            future.complete(asset);
        }

        void fail(Throwable error) {
            synchronized (this) {
                if (status != AssetStatus.QUEUED && status != AssetStatus.LOADING) {
                    return;
                }
                status = AssetStatus.FAILED;
            }
            future.completeExceptionally(error != null ? error : new FdxException("Asset load failed"));
        }

        void unload(Throwable error) {
            T unloadedAsset;
            synchronized (this) {
                if (status == AssetStatus.UNLOADED) {
                    return;
                }
                status = AssetStatus.UNLOADED;
                unloadedAsset = asset;
                asset = null;
            }
            disposeAsset(unloadedAsset);
            future.completeExceptionally(error);
        }
    }
}
