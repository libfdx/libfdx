package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.Objects;

public final class WebAsset {
    private final String path;
    private final long size;
    private final Path source;

    public WebAsset(String path, long size, Path source) {
        this.path = requirePath(path);
        this.size = requireSize(size, path);
        this.source = source != null ? source.toAbsolutePath().normalize() : null;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

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
