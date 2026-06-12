package io.github.libfdx.files;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for file system implementations.
 *
 * @author xpenatan
 */
public interface FileSystem extends ProviderHandle {
    /**
     * Runs the classpath step.
     *
     * @param path the asset or file path
     * @return the classpath
     */
    FileHandle classpath(String path);

    /**
     * Runs the internal step.
     *
     * @param path the asset or file path
     * @return the internal
     */
    FileHandle internal(String path);

    /**
     * Runs the local step.
     *
     * @param path the asset or file path
     * @return the local
     */
    FileHandle local(String path);

    /**
     * Runs the external step.
     *
     * @param path the asset or file path
     * @return the external
     */
    FileHandle external(String path);

    /**
     * Runs the cache step.
     *
     * @param path the asset or file path
     * @return the cache
     */
    FileHandle cache(String path);

    /**
     * Runs the temp step.
     *
     * @param prefix the prefix
     * @param suffix the suffix
     * @return the temp
     */
    FileHandle temp(String prefix, String suffix);

    /**
     * Runs the watch step.
     *
     * @param file the file handle or path
     * @return the watch
     */
    FdxFuture<FileWatch> watch(FileHandle file);
}
