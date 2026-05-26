package io.github.libfdx.files;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class DefaultFileSystem implements FileSystem {
    public static final ProviderId ID = ProviderId.of("default-files");

    private final File localRoot;
    private final File externalRoot;
    private final File cacheRoot;
    private final List<File> internalRoots = new ArrayList<File>();

    public DefaultFileSystem() {
        this(new File("."), new File("."), new File(System.getProperty("java.io.tmpdir")));
    }

    public DefaultFileSystem(File localRoot, File externalRoot, File cacheRoot) {
        this.localRoot = localRoot != null ? localRoot : new File(".");
        this.externalRoot = externalRoot != null ? externalRoot : this.localRoot;
        this.cacheRoot = cacheRoot != null ? cacheRoot : new File(System.getProperty("java.io.tmpdir"));
        addInternalRoot(new File("assets"));
        addInternalRoot(new File("tests/assets"));
        addInternalRoot(this.localRoot);
    }

    public DefaultFileSystem addInternalRoot(File root) {
        if (root != null) {
            internalRoots.add(root);
        }
        return this;
    }

    @Override
    public FileHandle classpath(String path) {
        return new DefaultFileHandle(this, FileLocation.CLASSPATH, normalize(path), null);
    }

    @Override
    public FileHandle internal(String path) {
        return new DefaultFileHandle(this, FileLocation.INTERNAL, normalize(path), null);
    }

    @Override
    public FileHandle local(String path) {
        return new DefaultFileHandle(this, FileLocation.LOCAL, normalize(path), new File(localRoot, normalize(path)));
    }

    @Override
    public FileHandle external(String path) {
        return new DefaultFileHandle(this, FileLocation.EXTERNAL, normalize(path), new File(externalRoot, normalize(path)));
    }

    @Override
    public FileHandle cache(String path) {
        return new DefaultFileHandle(this, FileLocation.CACHE, normalize(path), new File(cacheRoot, normalize(path)));
    }

    @Override
    public FileHandle temp(String prefix, String suffix) {
        try {
            File file = File.createTempFile(prefix != null ? prefix : "libfdx", suffix != null ? suffix : ".tmp", cacheRoot);
            return new DefaultFileHandle(this, FileLocation.TEMP, file.getPath(), file);
        } catch (IOException error) {
            throw new FdxException("Could not create temp file", error);
        }
    }

    @Override
    public FdxFuture<FileWatch> watch(FileHandle file) {
        return FdxFuture.failed(new FdxException("File watching is not supported by DefaultFileSystem"));
    }

    FdxFuture<byte[]> readBytes(final DefaultFileHandle handle) {
        try {
            InputStream input = openForRead(handle);
            if (input == null) {
                throw new FdxException("File not found: " + handle.path());
            }
            try {
                return FdxFuture.completed(readAll(input));
            } finally {
                input.close();
            }
        } catch (Throwable error) {
            return FdxFuture.failed(error);
        }
    }

    FdxFuture<Void> writeBytes(final DefaultFileHandle handle, final byte[] bytes, final boolean append) {
        try {
            File file = resolveFile(handle);
            if (file == null) {
                throw new FdxException("File location is not writable: " + handle.location());
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new FdxException("Could not create parent directory: " + parent);
            }
            FileOutputStream output = new FileOutputStream(file, append);
            try {
                output.write(bytes != null ? bytes : new byte[0]);
            } finally {
                output.close();
            }
            return FdxFuture.completed(null);
        } catch (Throwable error) {
            return FdxFuture.failed(error);
        }
    }

    File resolveFile(DefaultFileHandle handle) {
        if (handle.file != null) {
            return handle.file;
        }
        if (handle.location == FileLocation.INTERNAL) {
            for (int i = 0; i < internalRoots.size(); i++) {
                File file = new File(internalRoots.get(i), handle.path);
                if (file.exists()) {
                    return file;
                }
            }
            return new File(localRoot, handle.path);
        }
        return null;
    }

    InputStream openForRead(DefaultFileHandle handle) throws IOException {
        if (handle.location == FileLocation.CLASSPATH) {
            return classpathStream(handle.path);
        }
        if (handle.location == FileLocation.INTERNAL) {
            for (int i = 0; i < internalRoots.size(); i++) {
                File file = new File(internalRoots.get(i), handle.path);
                if (file.isFile()) {
                    return new FileInputStream(file);
                }
            }
            InputStream classpath = classpathStream(handle.path);
            if (classpath != null) {
                return classpath;
            }
        }
        File file = resolveFile(handle);
        if (file != null && file.isFile()) {
            return new FileInputStream(file);
        }
        return null;
    }

    boolean exists(DefaultFileHandle handle) {
        if (handle.location == FileLocation.CLASSPATH) {
            InputStream input = classpathStream(handle.path);
            if (input == null) {
                return false;
            }
            try {
                input.close();
            } catch (IOException ignored) {
            }
            return true;
        }
        File file = resolveFile(handle);
        return file != null && file.exists();
    }

    FileMetadata metadata(DefaultFileHandle handle) {
        File file = resolveFile(handle);
        if (file == null || !file.exists()) {
            return new FileMetadata(-1L, 0L, false);
        }
        return new FileMetadata(file.length(), file.lastModified(), file.isDirectory());
    }

    private InputStream classpathStream(String path) {
        File file = new File(normalize(path));
        if (!file.isFile()) {
            return null;
        }
        try {
            return new FileInputStream(file);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String normalize(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
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

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    static final class DefaultFileHandle implements FileHandle {
        private final DefaultFileSystem files;
        private final FileLocation location;
        private final String path;
        private final File file;

        DefaultFileHandle(DefaultFileSystem files, FileLocation location, String path, File file) {
            this.files = files;
            this.location = location;
            this.path = path != null ? path : "";
            this.file = file;
        }

        @Override
        public FileLocation location() {
            return location;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String name() {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }

        @Override
        public String extension() {
            String name = name();
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : "";
        }

        @Override
        public FileHandle parent() {
            int slash = path.lastIndexOf('/');
            return new DefaultFileHandle(files, location, slash >= 0 ? path.substring(0, slash) : "", file != null ? file.getParentFile() : null);
        }

        @Override
        public FileHandle child(String relativePath) {
            String child = relativePath != null ? relativePath.replace('\\', '/') : "";
            String joined = path.length() == 0 ? child : path + "/" + child;
            return new DefaultFileHandle(files, location, joined, file != null ? new File(file, child) : null);
        }

        @Override
        public boolean exists() {
            return files.exists(this);
        }

        @Override
        public boolean isDirectory() {
            File resolved = files.resolveFile(this);
            return resolved != null && resolved.isDirectory();
        }

        @Override
        public FdxFuture<FileMetadata> metadata() {
            return FdxFuture.completed(files.metadata(this));
        }

        @Override
        public FdxFuture<byte[]> readBytes() {
            return files.readBytes(this);
        }

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

        @Override
        public FdxFuture<Void> writeBytes(byte[] bytes, boolean append) {
            return files.writeBytes(this, bytes, append);
        }

        @Override
        public FdxFuture<Void> writeString(String text, Charset charset, boolean append) {
            byte[] bytes = (text != null ? text : "").getBytes(charset != null ? charset : Charset.defaultCharset());
            return writeBytes(bytes, append);
        }
    }

}
