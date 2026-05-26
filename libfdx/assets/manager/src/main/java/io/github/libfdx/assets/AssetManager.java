package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;

public interface AssetManager extends Disposable {
    <T> AssetHandle<T> load(AssetDescriptor<T> descriptor);

    <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor);

    boolean update();

    void finishLoading();

    <T> T get(String path, Class<T> type);

    <T> T find(String path, Class<T> type);

    void unload(String path);

    void registerLoader(Class<?> type, AssetLoader<?> loader);
}
