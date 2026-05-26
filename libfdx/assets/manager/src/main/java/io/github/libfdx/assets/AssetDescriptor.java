package io.github.libfdx.assets;

import io.github.libfdx.core.FdxException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public static <T> AssetDescriptor<T> of(String path, Class<T> type) {
        return new AssetDescriptor<T>(path, type, null);
    }

    public static <T> AssetDescriptor<T> of(String path, Class<T> type, Map<String, Object> options) {
        return new AssetDescriptor<T>(path, type, options);
    }

    public String path() {
        return path;
    }

    public Class<T> type() {
        return type;
    }

    public Map<String, Object> options() {
        return Collections.unmodifiableMap(options);
    }
}
