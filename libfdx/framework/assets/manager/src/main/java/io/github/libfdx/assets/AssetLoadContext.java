package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileSystem;

/**
 * Defines the contract for asset load context implementations.
 *
 * @author xpenatan
 */
public interface AssetLoadContext {
    /**
     * Returns the files.
     *
     * @return the files
     */
    FileSystem files();

    /**
     * Runs the dependency step.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the dependency
     */
    <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor);

    /**
     * Runs the complete on update step.
     *
     * @param <T> the value type
     * @param task the task
     * @return the complete on update
     */
    <T> FdxFuture<T> completeOnUpdate(FdxTask<T> task);
}
