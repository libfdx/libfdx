package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;

/**
 * Defines the contract for asset manager implementations.
 *
 * @author xpenatan
 */
public interface AssetManager extends Disposable {
    /**
     * Loads the requested resource.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
    <T> AssetHandle<T> load(AssetDescriptor<T> descriptor);

    /**
     * Starts loading the requested resource asynchronously.
     *
     * @param <T> the value type
     * @param descriptor the descriptor
     * @return the created value
     */
    <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor);

    /**
     * Updates this instance and reports whether work remains.
     *
     * @return true if more work remains; false otherwise
     */
    boolean update();

    /**
     * Finishes pending load work before returning.
     */
    void finishLoading();

    /**
     * Returns the loaded asset at the given path.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the loaded asset
     */
    <T> T get(String path, Class<T> type);

    /**
     * Finds a matching value.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the matching value, or null if none is available
     */
    <T> T find(String path, Class<T> type);

    /**
     * Unloads the resource at the given path.
     *
     * @param path the asset or file path
     */
    void unload(String path);

    /**
     * Registers an asset loader for a Java type.
     *
     * @param type the expected Java type
     * @param loader the loader to register
     */
    void registerLoader(Class<?> type, AssetLoader<?> loader);
}
