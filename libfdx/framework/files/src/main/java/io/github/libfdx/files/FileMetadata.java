package io.github.libfdx.files;

/**
 * Represents a file metadata.
 *
 * @author xpenatan
 */
public final class FileMetadata {
    private final long size;
    private final long lastModifiedMillis;
    private final boolean directory;

    /**
     * Creates a file metadata.
     *
     * @param size the size
     * @param lastModifiedMillis the last modified millis
     * @param directory the directory
     */
    public FileMetadata(long size, long lastModifiedMillis, boolean directory) {
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.directory = directory;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public long size() {
        return size;
    }

    /**
     * Returns the last modified millis.
     *
     * @return the last modified millis
     */
    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    /**
     * Returns whether directory is enabled or true.
     *
     * @return true if directory is enabled or true; false otherwise
     */
    public boolean isDirectory() {
        return directory;
    }
}
