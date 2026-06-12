package io.github.libfdx.assets;

import io.github.libfdx.core.FdxException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes the values used to create or identify an asset.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public final class AssetDescriptor<T> {
    private final String path;
    private final Class<T> type;
    private final Map<String, Object> options;

    private AssetDescriptor(String path, Class<T> type, Map<String, Object> options) {
        if (path == null || path.trim().length() == 0) {
            throw new FdxException("Asset path cannot be empty");
        }
        if (type == null) {
            throw new FdxException("Asset type cannot be null");
        }
        this.path = path.replace('\\', '/');
        this.type = type;
        this.options = options != null ? new LinkedHashMap<String, Object>(options) : new LinkedHashMap<String, Object>();
    }

    /**
     * Creates an asset descriptor from the supplied values.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @return the of
     */
    public static <T> AssetDescriptor<T> of(String path, Class<T> type) {
        return new AssetDescriptor<T>(path, type, null);
    }

    /**
     * Creates an asset descriptor from the supplied values.
     *
     * @param <T> the value type
     * @param path the asset or file path
     * @param type the expected Java type
     * @param options the options
     * @return the of
     */
    public static <T> AssetDescriptor<T> of(String path, Class<T> type, Map<String, Object> options) {
        return new AssetDescriptor<T>(path, type, options);
    }

    /**
     * Returns the path.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Returns the options.
     *
     * @return the options
     */
    public Map<String, Object> options() {
        return Collections.unmodifiableMap(options);
    }
}
