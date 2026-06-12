package io.github.libfdx.backend.android;

import android.app.Activity;
import android.content.res.AssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileLocation;
import io.github.libfdx.files.FileMetadata;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileWatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Represents an android file system.
 *
 * @author xpenatan
 */
final class AndroidFileSystem implements FileSystem {
    private static final ProviderId ID = ProviderId.of("android-files");

    private final AssetManager assets;
    private final DefaultFileSystem defaultFiles;

    AndroidFileSystem(Activity activity) {
        if (activity == null) {
            throw new FdxException("Android Activity cannot be null");
        }
        assets = activity.getAssets();
        File localRoot = activity.getFilesDir();
        File externalRoot = activity.getExternalFilesDir(null);
        File cacheRoot = activity.getCacheDir();
        defaultFiles = new DefaultFileSystem(localRoot, externalRoot != null ? externalRoot : localRoot, cacheRoot);
    }

    /**
     * Runs the classpath step.
     *
     * @param path the asset or file path
     * @return the classpath
     */
    @Override
    public FileHandle classpath(String path) {
        return defaultFiles.classpath(path);
    }

    /**
     * Runs the internal step.
     *
     * @param path the asset or file path
     * @return the internal
     */
    @Override
    public FileHandle internal(String path) {
        return new AndroidAssetFileHandle(normalize(path));
    }

    /**
     * Runs the local step.
     *
     * @param path the asset or file path
     * @return the local
     */
    @Override
    public FileHandle local(String path) {
        return defaultFiles.local(path);
    }

    /**
     * Runs the external step.
     *
     * @param path the asset or file path
     * @return the external
     */
    @Override
    public FileHandle external(String path) {
        return defaultFiles.external(path);
    }

    /**
     * Runs the cache step.
     *
     * @param path the asset or file path
     * @return the cache
     */
    @Override
    public FileHandle cache(String path) {
        return defaultFiles.cache(path);
    }

    /**
     * Runs the temp step.
     *
     * @param prefix the prefix
     * @param suffix the suffix
     * @return the temp
     */
    @Override
    public FileHandle temp(String prefix, String suffix) {
        return defaultFiles.temp(prefix, suffix);
    }

    /**
     * Runs the watch step.
     *
     * @param file the file handle or path
     * @return the watch
     */
    @Override
    public FdxFuture<FileWatch> watch(FileHandle file) {
        return FdxFuture.failed(new FdxException("File watching is not supported on Android yet"));
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return ID;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    private String normalize(String path) {
        if (path == null) {
            return "";
        }
        String value = path.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * Represents an android asset file handle.
     *
     * @author xpenatan
     */
    private final class AndroidAssetFileHandle implements FileHandle {
        private final String path;

        AndroidAssetFileHandle(String path) {
            this.path = path != null ? path : "";
        }

        /**
         * Returns the location.
         *
         * @return the location
         */
        @Override
        public FileLocation location() {
            return FileLocation.INTERNAL;
        }

        /**
         * Returns the path.
         *
         * @return the path
         */
        @Override
        public String path() {
            return path;
        }

        /**
         * Returns the name.
         *
         * @return the name
         */
        @Override
        public String name() {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }

        /**
         * Returns the extension.
         *
         * @return the extension
         */
        @Override
        public String extension() {
            String name = name();
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : "";
        }

        /**
         * Returns the parent.
         *
         * @return the parent
         */
        @Override
        public FileHandle parent() {
            int slash = path.lastIndexOf('/');
            return new AndroidAssetFileHandle(slash >= 0 ? path.substring(0, slash) : "");
        }

        /**
         * Runs the child step.
         *
         * @param relativePath the relative path
         * @return the child
         */
        @Override
        public FileHandle child(String relativePath) {
            String child = normalize(relativePath);
            return new AndroidAssetFileHandle(path.length() == 0 ? child : path + "/" + child);
        }

        /**
         * Returns the exists.
         *
         * @return true if exists succeeds or is active; false otherwise
         */
        @Override
        public boolean exists() {
            InputStream input = null;
            try {
                input = assets.open(path);
                return true;
            } catch (IOException ignored) {
                try {
                    String[] children = assets.list(path);
                    return children != null && children.length > 0;
                } catch (IOException ignoredToo) {
                    return false;
                }
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        /**
         * Returns whether directory is enabled or true.
         *
         * @return true if directory is enabled or true; false otherwise
         */
        @Override
        public boolean isDirectory() {
            try {
                String[] children = assets.list(path);
                return children != null && children.length > 0;
            } catch (IOException ignored) {
                return false;
            }
        }

        /**
         * Returns the metadata.
         *
         * @return the metadata
         */
        @Override
        public FdxFuture<FileMetadata> metadata() {
            return FdxFuture.completed(new FileMetadata(-1L, 0L, isDirectory()));
        }

        /**
         * Returns the read bytes.
         *
         * @return the read bytes
         */
        @Override
        public FdxFuture<byte[]> readBytes() {
            try {
                InputStream input = assets.open(path);
                try {
                    return FdxFuture.completed(readAll(input));
                } finally {
                    input.close();
                }
            } catch (Throwable error) {
                return FdxFuture.failed(error);
            }
        }

        /**
         * Runs the read string step.
         *
         * @param charset the charset
         * @return the read string
         */
        @Override
        public FdxFuture<String> readString(final Charset charset) {
            FdxFuture<byte[]> bytes = readBytes();
            if (bytes.isFailed()) {
                try {
                    bytes.get();
                } catch (RuntimeException error) {
                    return FdxFuture.failed(error);
                }
            }
            return FdxFuture.completed(new String(bytes.get(), charset != null ? charset : Charset.defaultCharset()));
        }

        /**
         * Runs the write bytes step.
         *
         * @param bytes the bytes
         * @param append the append
         * @return the write bytes
         */
        @Override
        public FdxFuture<Void> writeBytes(byte[] bytes, boolean append) {
            return FdxFuture.failed(new FdxException("Android internal assets are read-only: " + path));
        }

        /**
         * Runs the write string step.
         *
         * @param text the text
         * @param charset the charset
         * @param append the append
         * @return the write string
         */
        @Override
        public FdxFuture<Void> writeString(String text, Charset charset, boolean append) {
            return FdxFuture.failed(new FdxException("Android internal assets are read-only: " + path));
        }
    }

}
