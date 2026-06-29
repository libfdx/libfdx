package io.github.libfdx.storage;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the persistent storage service.
 *
 * @author xpenatan
 */
public interface Storage extends ProviderHandle {
    /**
     * Opens durable user-owned local storage.
     *
     * @param name the store name
     * @return the store
     */
    KeyValueStore local(String name);

    /**
     * Opens durable user-owned local storage with a byte transform.
     *
     * @param name the store name
     * @param codec the storage codec
     * @return the store
     */
    KeyValueStore local(String name, StorageCodec codec);

    /**
     * Opens rebuildable cache storage.
     *
     * @param name the store name
     * @return the store
     */
    KeyValueStore cache(String name);

    /**
     * Opens rebuildable cache storage with a byte transform.
     *
     * @param name the store name
     * @param codec the storage codec
     * @return the store
     */
    KeyValueStore cache(String name, StorageCodec codec);
}
