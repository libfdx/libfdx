package io.github.libfdx.storage;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;

/**
 * Persists storage stores through a {@link FileSystem}.
 *
 * @author xpenatan
 */
public final class FileStorageBackend implements StorageBackend {
    public static final ProviderId ID = ProviderId.of("file-storage");

    private final FileSystem files;

    public FileStorageBackend(FileSystem files) {
        if (files == null) {
            throw new FdxException("FileSystem cannot be null");
        }
        this.files = files;
    }

    @Override
    public byte[] read(StorageScope scope, String path) {
        FileHandle file = file(scope, path);
        if (!file.exists()) {
            return null;
        }
        return file.readBytes().join();
    }

    @Override
    public void write(StorageScope scope, String path, byte[] bytes) {
        file(scope, path).writeBytes(bytes, false).join();
    }

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    private FileHandle file(StorageScope scope, String path) {
        if (scope == StorageScope.CACHE) {
            return files.cache(path);
        }
        return files.local(path);
    }
}
