package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileLocation;
import io.github.libfdx.files.FileMetadata;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileWatch;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a web file system.
 *
 * @author xpenatan
 */
public final class WebFileSystem implements FileSystem {
    public static final ProviderId ID = ProviderId.of("web-files");

    private final Map<String, byte[]> writableFiles = new HashMap<String, byte[]>();

    /**
     * Runs the classpath step.
     *
     * @param path the asset or file path
     * @return the classpath
     */
    @Override
    public FileHandle classpath(String path) {
        return new WebFileHandle(this, FileLocation.CLASSPATH, normalize(path));
    }

    /**
     * Runs the internal step.
     *
     * @param path the asset or file path
     * @return the internal
     */
    @Override
    public FileHandle internal(String path) {
        return new WebFileHandle(this, FileLocation.INTERNAL, normalize(path));
    }

    /**
     * Runs the local step.
     *
     * @param path the asset or file path
     * @return the local
     */
    @Override
    public FileHandle local(String path) {
        return new WebFileHandle(this, FileLocation.LOCAL, normalize(path));
    }

    /**
     * Runs the external step.
     *
     * @param path the asset or file path
     * @return the external
     */
    @Override
    public FileHandle external(String path) {
        return new WebFileHandle(this, FileLocation.EXTERNAL, normalize(path));
    }

    /**
     * Runs the cache step.
     *
     * @param path the asset or file path
     * @return the cache
     */
    @Override
    public FileHandle cache(String path) {
        return new WebFileHandle(this, FileLocation.CACHE, normalize(path));
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
        String name = (prefix != null && prefix.length() > 0 ? prefix : "libfdx")
                + System.currentTimeMillis()
                + (suffix != null ? suffix : ".tmp");
        return new WebFileHandle(this, FileLocation.TEMP, normalize(name));
    }

    /**
     * Runs the watch step.
     *
     * @param file the file handle or path
     * @return the watch
     */
    @Override
    public FdxFuture<FileWatch> watch(FileHandle file) {
        return FdxFuture.failed(new FdxException("File watching is not supported by WebFileSystem"));
    }

    FdxFuture<byte[]> readBytes(WebFileHandle handle) {
        try {
            byte[] bytes = readBytesNow(handle);
            if (bytes == null) {
                throw new FdxException("File not found: " + handle.path());
            }
            return FdxFuture.completed(bytes);
        } catch (Throwable error) {
            return FdxFuture.failed(error);
        }
    }

    FdxFuture<Void> writeBytes(WebFileHandle handle, byte[] bytes, boolean append) {
        if (!isWritable(handle.location)) {
            return FdxFuture.failed(new FdxException("File location is not writable on web: " + handle.location));
        }
        String path = handle.path;
        byte[] source = bytes != null ? bytes : new byte[0];
        if (append && writableFiles.containsKey(path)) {
            byte[] existing = writableFiles.get(path);
            byte[] combined = new byte[existing.length + source.length];
            System.arraycopy(existing, 0, combined, 0, existing.length);
            System.arraycopy(source, 0, combined, existing.length, source.length);
            writableFiles.put(path, combined);
        } else {
            byte[] copy = new byte[source.length];
            System.arraycopy(source, 0, copy, 0, source.length);
            writableFiles.put(path, copy);
        }
        return FdxFuture.completed(null);
    }

    boolean exists(WebFileHandle handle) {
        if (isWritable(handle.location)) {
            return writableFiles.containsKey(handle.path);
        }
        return webAssetExists(handle.path);
    }

    boolean isDirectory(WebFileHandle handle) {
        return handle.path.length() == 0;
    }

    FileMetadata metadata(WebFileHandle handle) {
        byte[] writable = isWritable(handle.location) ? writableFiles.get(handle.path) : null;
        if (writable != null) {
            return new FileMetadata(writable.length, 0L, false);
        }
        int size = webAssetLength(handle.path);
        return new FileMetadata(size >= 0 ? size : -1L, 0L, false);
    }

    private byte[] readBytesNow(WebFileHandle handle) {
        if (isWritable(handle.location)) {
            byte[] bytes = writableFiles.get(handle.path);
            if (bytes == null) {
                return null;
            }
            byte[] copy = new byte[bytes.length];
            System.arraycopy(bytes, 0, copy, 0, bytes.length);
            return copy;
        }
        if (handle.location == FileLocation.EXTERNAL) {
            return null;
        }
        Int8Array data = webAssetBytes(handle.path);
        return data != null ? data.copyToJavaArray() : null;
    }

    private boolean isWritable(FileLocation location) {
        return location == FileLocation.LOCAL || location == FileLocation.CACHE || location == FileLocation.TEMP;
    }

    private String normalize(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        return normalized;
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

    @JSBody(params = { "path" }, script =
            "path = normalizeLibfdxPath(path);\n" +
            "var cached = libfdxFindAsset(path);\n" +
            "if (cached) return cached;\n" +
            "try {\n" +
            "  var request = new XMLHttpRequest();\n" +
            "  request.open('GET', 'assets/' + path, false);\n" +
            "  request.overrideMimeType('text/plain; charset=x-user-defined');\n" +
            "  request.send(null);\n" +
            "  var ok = (request.status >= 200 && request.status < 300) || request.status === 0;\n" +
            "  if (!ok || request.responseText == null) return null;\n" +
            "  var text = request.responseText;\n" +
            "  var data = new Int8Array(text.length);\n" +
            "  for (var i = 0; i < text.length; i++) data[i] = text.charCodeAt(i) & 255;\n" +
            "  return data;\n" +
            "} catch (error) {\n" +
            "  return null;\n" +
            "}\n" +
            "function normalizeLibfdxPath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}\n" +
            "function libfdxFindAsset(value) {\n" +
            "  var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "  var assets = root.libfdxAssets;\n" +
            "  if (!assets) return null;\n" +
            "  var stored = assets[value] || assets['assets/' + value];\n" +
            "  if (!stored) return null;\n" +
            "  if (stored instanceof Int8Array) return stored;\n" +
            "  if (stored instanceof Uint8Array) return new Int8Array(stored.buffer, stored.byteOffset, stored.byteLength);\n" +
            "  return new Int8Array(stored);\n" +
            "}")
    private static native Int8Array webAssetBytes(String path);

    @JSBody(params = { "path" }, script =
            "path = normalizeLibfdxPath(path);\n" +
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var manifest = root.libfdxAssetManifest;\n" +
            "var assets = root.libfdxAssets;\n" +
            "if ((manifest && (manifest[path] || manifest['assets/' + path])) || " +
            "    (assets && (assets[path] || assets['assets/' + path]))) return true;\n" +
            "try {\n" +
            "  var request = new XMLHttpRequest();\n" +
            "  request.open('HEAD', 'assets/' + path, false);\n" +
            "  request.send(null);\n" +
            "  return (request.status >= 200 && request.status < 300) || request.status === 0;\n" +
            "} catch (error) {\n" +
            "  return false;\n" +
            "}\n" +
            "function normalizeLibfdxPath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}")
    private static native boolean webAssetExists(String path);

    @JSBody(params = { "path" }, script =
            "path = normalizeLibfdxPath(path);\n" +
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var manifest = root.libfdxAssetManifest;\n" +
            "var entry = manifest && (manifest[path] || manifest['assets/' + path]);\n" +
            "if (entry && typeof entry.size === 'number') return entry.size;\n" +
            "var assets = root.libfdxAssets;\n" +
            "var stored = assets && (assets[path] || assets['assets/' + path]);\n" +
            "if (stored) return stored.byteLength || stored.length || -1;\n" +
            "return -1;\n" +
            "function normalizeLibfdxPath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}")
    private static native int webAssetLength(String path);

    /**
     * Represents a web file handle.
     *
     * @author xpenatan
     */
    static final class WebFileHandle implements FileHandle {
        private final WebFileSystem files;
        private final FileLocation location;
        private final String path;

        WebFileHandle(WebFileSystem files, FileLocation location, String path) {
            this.files = files;
            this.location = location;
            this.path = path != null ? path : "";
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
            return new WebFileHandle(files, location, slash >= 0 ? path.substring(0, slash) : "");
        }

        /**
         * Runs the child step.
         *
         * @param relativePath the relative path
         * @return the child
         */
        @Override
        public FileHandle child(String relativePath) {
            String child = files.normalize(relativePath);
            String joined = path.length() == 0 ? child : path + "/" + child;
            return new WebFileHandle(files, location, files.normalize(joined));
        }

        /**
         * Returns the exists.
         *
         * @return true if exists succeeds or is active; false otherwise
         */
        @Override
        public boolean exists() {
            return files.exists(this);
        }

        /**
         * Returns whether directory is enabled or true.
         *
         * @return true if directory is enabled or true; false otherwise
         */
        @Override
        public boolean isDirectory() {
            return files.isDirectory(this);
        }

        /**
         * Returns the metadata.
         *
         * @return the metadata
         */
        @Override
        public FdxFuture<FileMetadata> metadata() {
            return FdxFuture.completed(files.metadata(this));
        }

        /**
         * Returns the read bytes.
         *
         * @return the read bytes
         */
        @Override
        public FdxFuture<byte[]> readBytes() {
            return files.readBytes(this);
        }

        /**
         * Runs the read string step.
         *
         * @param charset the charset
         * @return the read string
         */
        @Override
        public FdxFuture<String> readString(Charset charset) {
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
            return files.writeBytes(this, bytes, append);
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
            byte[] bytes = (text != null ? text : "").getBytes(charset != null ? charset : Charset.defaultCharset());
            return writeBytes(bytes, append);
        }
    }
}
