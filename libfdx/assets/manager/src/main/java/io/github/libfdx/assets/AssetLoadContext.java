package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileSystem;

public interface AssetLoadContext {
    FileSystem files();

    <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor);

    <T> FdxFuture<T> completeOnUpdate(FdxTask<T> task);
}
