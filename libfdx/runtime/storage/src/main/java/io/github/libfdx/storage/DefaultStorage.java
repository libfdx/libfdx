package io.github.libfdx.storage;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.FileSystem;

/**
 * Provides persistent key/value storage.
 *
 * @author xpenatan
 */
public final class DefaultStorage implements Storage {
    private static final String ROOT = "storage";

    private final StorageBackend backend;

    public DefaultStorage(FileSystem files) {
        this(new FileStorageBackend(files));
    }

    public DefaultStorage(StorageBackend backend) {
        if (backend == null) {
            throw new FdxException("StorageBackend cannot be null");
        }
        this.backend = backend;
    }

    @Override
    public KeyValueStore local(String name) {
        return local(name, StorageCodecs.identity());
    }

    @Override
    public KeyValueStore local(String name, StorageCodec codec) {
        return store(StorageScope.LOCAL, name, codec);
    }

    @Override
    public KeyValueStore cache(String name) {
        return cache(name, StorageCodecs.identity());
    }

    @Override
    public KeyValueStore cache(String name, StorageCodec codec) {
        return store(StorageScope.CACHE, name, codec);
    }

    @Override
    public ProviderId providerId() {
        return backend.providerId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    private KeyValueStore store(StorageScope scope, String name, StorageCodec codec) {
        String storeName = normalizeName(name);
        return new JsonKeyValueStore(backend, scope, storeName, path(storeName), codec);
    }

    private static String path(String name) {
        String path = ROOT + "/" + name;
        return path.endsWith(".json") ? path : path + ".json";
    }

    private static String normalizeName(String name) {
        String value = name != null ? name.replace('\\', '/').trim() : "";
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        String[] segments = value.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.length() == 0 || ".".equals(segment) || "..".equals(segment)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(segment);
        }
        return builder.length() > 0 ? builder.toString() : "default";
    }
}
