package io.github.libfdx.files;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for file watch implementations.
 *
 * @author xpenatan
 */
public interface FileWatch extends ProviderHandle, Disposable {
    /**
     * Returns the file.
     *
     * @return the file
     */
    FileHandle file();

    /**
     * Adds the listener.
     *
     * @param listener the listener
     */
    void addListener(FileWatchListener listener);

    /**
     * Removes the listener.
     *
     * @param listener the listener
     */
    void removeListener(FileWatchListener listener);
}
