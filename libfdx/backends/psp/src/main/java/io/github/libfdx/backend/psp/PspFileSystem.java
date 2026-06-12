package io.github.libfdx.backend.psp;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.backend.psp.natives.PSPFileApi;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileLocation;
import io.github.libfdx.files.FileMetadata;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileWatch;

import java.nio.charset.Charset;

/**
 * Represents a psp file system.
 *
 * @author xpenatan
 */
final class PspFileSystem implements FileSystem {
    private static final ProviderId ID = ProviderId.of("psp_files");

    /**
     * Runs the classpath step.
     *
     * @param path the asset or file path
     * @return the classpath
     */
    @Override
    public FileHandle classpath(String path) {
        return assetHandle(FileLocation.CLASSPATH, path);
    }

    /**
     * Runs the internal step.
     *
     * @param path the asset or file path
     * @return the internal
     */
    @Override
    public FileHandle internal(String path) {
        return assetHandle(FileLocation.INTERNAL, path);
    }

    /**
     * Runs the local step.
     *
     * @param path the asset or file path
     * @return the local
     */
    @Override
    public FileHandle local(String path) {
        return handle(FileLocation.LOCAL, path);
    }

    /**
     * Runs the external step.
     *
     * @param path the asset or file path
     * @return the external
     */
    @Override
    public FileHandle external(String path) {
        return handle(FileLocation.EXTERNAL, path);
    }

    /**
     * Runs the cache step.
     *
     * @param path the asset or file path
     * @return the cache
     */
    @Override
    public FileHandle cache(String path) {
        return handle(FileLocation.CACHE, path);
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
        String actualPrefix = prefix != null ? prefix : "libfdx";
        String actualSuffix = suffix != null ? suffix : ".tmp";
        return handle(FileLocation.TEMP, actualPrefix + actualSuffix);
    }

    /**
     * Runs the watch step.
     *
     * @param file the file handle or path
     * @return the watch
     */
    @Override
    public FdxFuture<FileWatch> watch(FileHandle file) {
        return FdxFuture.failed(unsupported(file != null ? file.location() : null, file != null ? file.path() : ""));
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

    private PspFileHandle handle(FileLocation location, String path) {
        FileLocation actualLocation = location != null ? location : FileLocation.INTERNAL;
        return new PspFileHandle(actualLocation, normalize(path), false);
    }

    private PspFileHandle assetHandle(FileLocation location, String path) {
        FileLocation actualLocation = location != null ? location : FileLocation.INTERNAL;
        return new PspFileHandle(actualLocation, normalize(path), true);
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        if (isNormalizedPath(path)) {
            return path;
        }
        StringBuilder result = new StringBuilder();
        int segmentStart = 0;
        int length = path.length();
        for (int i = 0; i <= length; i++) {
            boolean atEnd = i == length;
            char ch = atEnd ? '/' : path.charAt(i);
            if (ch != '/' && ch != '\\') {
                continue;
            }
            int segmentLength = i - segmentStart;
            if (segmentLength == 2 && path.charAt(segmentStart) == '.' && path.charAt(segmentStart + 1) == '.') {
                return "";
            }
            if (segmentLength != 0 && !(segmentLength == 1 && path.charAt(segmentStart) == '.')) {
                if (result.length() > 0) {
                    result.append('/');
                }
                for (int segmentIndex = segmentStart; segmentIndex < i; segmentIndex++) {
                    result.append(path.charAt(segmentIndex));
                }
            }
            segmentStart = i + 1;
        }
        return result.toString();
    }

    private static boolean isNormalizedPath(String path) {
        int segmentStart = 0;
        int length = path.length();
        if (length == 0 || path.charAt(0) == '/' || path.charAt(0) == '\\') {
            return false;
        }
        for (int i = 0; i <= length; i++) {
            boolean atEnd = i == length;
            char ch = atEnd ? '/' : path.charAt(i);
            if (ch == '\\') {
                return false;
            }
            if (ch != '/') {
                continue;
            }
            int segmentLength = i - segmentStart;
            if (segmentLength == 0) {
                return false;
            }
            if (segmentLength == 1 && path.charAt(segmentStart) == '.') {
                return false;
            }
            if (segmentLength == 2 && path.charAt(segmentStart) == '.' && path.charAt(segmentStart + 1) == '.') {
                return false;
            }
            segmentStart = i + 1;
        }
        return true;
    }

    private static FdxException unsupported(FileLocation location, String path) {
        return new FdxException("PSP file system supports read-only internal/classpath assets only: "
                + location + ":" + path);
    }

    private static FdxException missing(FileLocation location, String path) {
        return new FdxException("PSP asset was not found: " + location + ":" + path);
    }

    private static boolean isAssetLocation(FileLocation location) {
        return location == FileLocation.INTERNAL || location == FileLocation.CLASSPATH;
    }

    private static int assetSize(char[] path) {
        if (path == null || path.length == 0) {
            return -1;
        }
        return PSPFileApi.assetSize(path, path.length);
    }

    private static char[] nativeAssetPath(String path) {
        if (path == null || path.length() == 0 || startsWithAssetsPrefix(path)) {
            return path != null ? path.toCharArray() : new char[0];
        }
        return ("assets/" + path).toCharArray();
    }

    private static boolean startsWithAssetsPrefix(String path) {
        return path.length() >= 7
                && path.charAt(0) == 'a'
                && path.charAt(1) == 's'
                && path.charAt(2) == 's'
                && path.charAt(3) == 'e'
                && path.charAt(4) == 't'
                && path.charAt(5) == 's'
                && path.charAt(6) == '/';
    }

    /**
     * Represents a psp file handle.
     *
     * @author xpenatan
     */
    private static final class PspFileHandle implements FileHandle {
        private final FileLocation location;
        private final String path;
        private final char[] nativePath;
        private final boolean assetLocation;

        PspFileHandle(FileLocation location, String path, boolean assetLocation) {
            this.location = location != null ? location : FileLocation.INTERNAL;
            this.path = path != null ? path : "";
            this.assetLocation = assetLocation;
            this.nativePath = assetLocation ? nativeAssetPath(this.path) : this.path.toCharArray();
        }

        /**
         * Returns the location.
         *
         * @return the location
         */
        @Override
        public FileLocation location() {
            return location;
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
            return new PspFileHandle(location, slash >= 0 ? path.substring(0, slash) : "", assetLocation);
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
            return new PspFileHandle(location, path.length() == 0 ? child : path + "/" + child, assetLocation);
        }

        /**
         * Returns the exists.
         *
         * @return true if exists succeeds or is active; false otherwise
         */
        @Override
        public boolean exists() {
            return assetLocation && assetSize(nativePath) >= 0;
        }

        /**
         * Returns whether directory is enabled or true.
         *
         * @return true if directory is enabled or true; false otherwise
         */
        @Override
        public boolean isDirectory() {
            return false;
        }

        /**
         * Returns the metadata.
         *
         * @return the metadata
         */
        @Override
        public FdxFuture<FileMetadata> metadata() {
            int size = assetLocation ? assetSize(nativePath) : -1;
            if (size < 0) {
                return FdxFuture.completed(new FileMetadata(-1L, 0L, false));
            }
            return FdxFuture.completed(new FileMetadata(size, 0L, false));
        }

        /**
         * Returns the read bytes.
         *
         * @return the read bytes
         */
        @Override
        public FdxFuture<byte[]> readBytes() {
            if (!assetLocation) {
                return FdxFuture.failed(unsupported(location, path));
            }
            byte[] bytes = readAsset();
            if (bytes == null) {
                return FdxFuture.failed(missing(location, path));
            }
            return FdxFuture.completed(bytes);
        }

        /**
         * Runs the read string step.
         *
         * @param charset the charset
         * @return the read string
         */
        @Override
        public FdxFuture<String> readString(Charset charset) {
            if (charset == null) {
                return FdxFuture.failed(new FdxException("Charset cannot be null"));
            }
            if (!assetLocation) {
                return FdxFuture.failed(unsupported(location, path));
            }
            byte[] bytes = readAsset();
            if (bytes == null) {
                return FdxFuture.failed(missing(location, path));
            }
            return FdxFuture.completed(new String(bytes, charset));
        }

        private byte[] readAsset() {
            int size = assetSize(nativePath);
            if (size < 0) {
                return null;
            }
            byte[] bytes = new byte[size];
            int read = PSPFileApi.assetRead(nativePath, nativePath.length, bytes, bytes.length);
            if (read != bytes.length) {
                throw new FdxException("Could not read complete PSP asset: " + path);
            }
            return bytes;
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
            return FdxFuture.failed(unsupported(location, path));
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
            return FdxFuture.failed(unsupported(location, path));
        }
    }
}
