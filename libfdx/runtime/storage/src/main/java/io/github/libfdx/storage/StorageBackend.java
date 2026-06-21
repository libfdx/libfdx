package io.github.libfdx.storage;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the low-level persistence bridge used by storage implementations.
 *
 * @author xpenatan
 */
public interface StorageBackend extends ProviderHandle {
    /**
     * Reads stored bytes.
     *
     * @param scope the storage scope
     * @param path the normalized store path
     * @return the stored bytes, or null when the store does not exist
     */
    byte[] read(StorageScope scope, String path);

    /**
     * Writes stored bytes.
     *
     * @param scope the storage scope
     * @param path the normalized store path
     * @param bytes the stored bytes
     */
    void write(StorageScope scope, String path, byte[] bytes);
}
