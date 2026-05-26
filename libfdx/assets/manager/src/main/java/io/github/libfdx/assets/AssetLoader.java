package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;

public interface AssetLoader<T> {
    Class<T> type();

    FdxFuture<T> load(AssetLoadContext context, AssetDescriptor<T> descriptor);
}
