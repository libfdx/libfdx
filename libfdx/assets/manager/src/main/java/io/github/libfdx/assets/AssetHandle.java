package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;

public interface AssetHandle<T> {
    AssetDescriptor<T> descriptor();

    AssetStatus status();

    boolean isLoaded();

    T asset();

    FdxFuture<T> future();
}
