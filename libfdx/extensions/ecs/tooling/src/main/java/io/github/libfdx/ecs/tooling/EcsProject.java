package io.github.libfdx.ecs.tooling;

import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;

/** Portable metadata, schema, and runtime factory for a libFDX ECS project. */
public abstract class EcsProject {
    private final String id;
    private final String name;
    private final String assetsPath;
    private final String defaultScene;

    protected EcsProject(String id, String name, String assetsPath, String defaultScene) {
        this.id = requireIdentifier(id, "id");
        this.name = requireText(name, "name");
        this.assetsPath = normalizeRelativePath(assetsPath, "assetsPath");
        this.defaultScene = normalizeRelativePath(defaultScene, "defaultScene");
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final String assetsPath() {
        return assetsPath;
    }

    public final String defaultScene() {
        return defaultScene;
    }

    public abstract EcsProjectSchema schema();

    /** Creates a new independent, initially uninitialized runtime. */
    public abstract EcsProjectRuntime createRuntime();

    /** Normalizes a portable project-relative path and rejects traversal. */
    public static String normalizeRelativePath(String path, String label) {
        String normalized = requireText(path, label).replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException(label + " must be project-relative: " + path);
        }
        String[] parts = normalized.split("/", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException(label + " contains an invalid path segment: " + path);
            }
            if (i > 0) {
                result.append('/');
            }
            result.append(part);
        }
        return result.toString();
    }

    private static String requireIdentifier(String value, String label) {
        String text = requireText(value, label);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '.' && c != '_' && c != '-') {
                throw new IllegalArgumentException(label + " contains an invalid character: " + value);
            }
        }
        return text;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }
}
