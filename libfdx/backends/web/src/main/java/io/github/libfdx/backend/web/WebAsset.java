package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a web asset.
 *
 * @author xpenatan
 */
public final class WebAsset {
    private final String path;
    private final long size;
    private final Path source;

    /**
     * Creates a web asset.
     *
     * @param path the asset or file path
     * @param size the size
     * @param source the source value
     */
    public WebAsset(String path, long size, Path source) {
        this.path = requirePath(path);
        this.size = requireSize(size, path);
        this.source = source != null ? source.toAbsolutePath().normalize() : null;
    }

    /**
     * Returns the path.
     *
     * @return the get path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the size.
     *
     * @return the get size
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the source.
     *
     * @return the get source
     */
    public Path getSource() {
        return source;
    }

    private static String requirePath(String path) {
        Objects.requireNonNull(path, "path");
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Web asset path cannot be blank");
        }
        if (normalized.contains("..")) {
            for (String segment : normalized.split("/")) {
                if (segment.equals("..")) {
                    throw new IllegalArgumentException("Web asset path cannot contain '..': " + path);
                }
            }
        }
        return normalized;
    }

    private static long requireSize(long size, String path) {
        if (size < 0) {
            throw new IllegalArgumentException("Web asset size cannot be negative for " + path + ": " + size);
        }
        return size;
    }
}
