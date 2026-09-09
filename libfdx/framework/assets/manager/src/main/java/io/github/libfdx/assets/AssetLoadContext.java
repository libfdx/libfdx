package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileHandle;

/**
 * Defines the contract for asset load context implementations.
 * Context methods belong to the application thread. CPU tasks may acquire or
 * decode input, then their queued callbacks may declare additional dependencies.
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
     * Requests a borrowed dependency and retains it for the parent load/asset.
     * Declare dependencies before the first {@link #completeOnUpdate} call.
     * The default manager detects cycles and waits for all dependencies before
     * running finalization, including when their futures are still pending.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the dependency
     */
    <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor);

    /**
     * Closes dependency discovery and queues application-thread finalization.
     * The task runs only after all declared dependencies succeed, within the
     * manager's update budget. It must return an owned result; borrowed
     * dependencies must not be disposed by that result. A throwing task is
     * responsible for releasing resources it partially created.
     *
     * @param <T> the value type
     * @param task the task
     * @return the complete on update
     */
    <T> FdxFuture<T> completeOnUpdate(FdxTask<T> task);

    /**
     * Schedules nonblocking CPU preparation on the configured executor, or as
     * one cooperative update step when no executor was supplied. Result/failure
     * delivery is queued on the application thread. Do not wait on asset futures
     * or access graphics here. Cancellation cannot interrupt a running task.
     * An owned Disposable CPU result arriving after cancellation is disposed on
     * its producing thread; such results must permit that cleanup.
     * Successful delivery transfers ownership to the loader, which must release
     * staging data after use, including failure/cancellation of a later stage.
     *
     * @param task the non-null CPU task
     * @param <T> the result type
     * @return the pending preparation result
     */
    <T> FdxFuture<T> async(FdxTask<T> task);

    /**
     * Starts whole-file acquisition on the configured executor/cooperative queue
     * and composes the file's future without blocking. Completion is delivered
     * through the application update queue, even for an already complete read.
     *
     * @param file the borrowed non-null file handle
     * @return the owned encoded bytes
     */
    FdxFuture<byte[]> readBytes(FileHandle file);

    /**
     * Starts provider-asynchronous preparation through the same executor and update
     * budget as {@link #async}. The task must return promptly and must not block on
     * its result. Owned Disposable results have the same cancellation/transfer rules.
     * Useful for opening bounded file sources or reading/decompressing one chunk.
     */
    default <T> FdxFuture<T> asyncFuture(FdxTask<FdxFuture<T>> task) {
        throw new UnsupportedOperationException("This asset context does not support asynchronous future preparation");
    }
}
