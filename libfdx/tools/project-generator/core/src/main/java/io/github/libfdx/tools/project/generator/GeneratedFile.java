package io.github.libfdx.tools.project.generator;

public final class GeneratedFile {
    private final String path;
    private final String textContent;
    private final byte[] binaryContent;

    private GeneratedFile(String path, String textContent, byte[] binaryContent) {
        this.path = normalizePath(path);
        this.textContent = textContent;
        this.binaryContent = binaryContent != null ? binaryContent.clone() : null;
        if (this.path.length() == 0) {
            throw new IllegalArgumentException("Generated file path cannot be empty.");
        }
        if (this.textContent == null && this.binaryContent == null) {
            throw new IllegalArgumentException("Generated file content cannot be null.");
        }
    }

    public static GeneratedFile text(String path, String content) {
        return new GeneratedFile(path, content != null ? content : "", null);
    }

    public static GeneratedFile binary(String path, byte[] content) {
        return new GeneratedFile(path, null, content != null ? content : new byte[0]);
    }

    public String path() {
        return path;
    }

    public boolean isText() {
        return textContent != null;
    }

    public String textContent() {
        return textContent;
    }

    public byte[] binaryContent() {
        return binaryContent != null ? binaryContent.clone() : null;
    }

    private static String normalizePath(String value) {
        String path = value != null ? value.trim().replace('\\', '/') : "";
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.contains("../") || path.equals("..") || path.endsWith("/..")) {
            throw new IllegalArgumentException("Generated file path cannot traverse parent directories: " + value);
        }
        return path;
    }
}
