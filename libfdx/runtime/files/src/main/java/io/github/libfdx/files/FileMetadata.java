package io.github.libfdx.files;

public final class FileMetadata {
    private final long size;
    private final long lastModifiedMillis;
    private final boolean directory;

    public FileMetadata(long size, long lastModifiedMillis, boolean directory) {
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.directory = directory;
    }

    public long size() {
        return size;
    }

    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    public boolean isDirectory() {
        return directory;
    }
}
