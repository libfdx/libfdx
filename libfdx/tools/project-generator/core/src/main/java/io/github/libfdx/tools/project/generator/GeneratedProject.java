package io.github.libfdx.tools.project.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeneratedProject {
    private final String name;
    private final List<GeneratedFile> files;
    private final Map<String, GeneratedFile> filesByPath;

    public GeneratedProject(String name, List<GeneratedFile> files) {
        this.name = name != null ? name : "";
        this.files = Collections.unmodifiableList(new ArrayList<GeneratedFile>(files));
        LinkedHashMap<String, GeneratedFile> byPath = new LinkedHashMap<String, GeneratedFile>();
        for (int i = 0; i < this.files.size(); i++) {
            GeneratedFile file = this.files.get(i);
            if (byPath.put(file.path(), file) != null) {
                throw new IllegalArgumentException("Duplicate generated file path: " + file.path());
            }
        }
        this.filesByPath = Collections.unmodifiableMap(byPath);
    }

    public String name() {
        return name;
    }

    public List<GeneratedFile> files() {
        return files;
    }

    public int fileCount() {
        return files.size();
    }

    public GeneratedFile file(String path) {
        return filesByPath.get(normalizeLookupPath(path));
    }

    public boolean containsFile(String path) {
        return file(path) != null;
    }

    private static String normalizeLookupPath(String value) {
        String path = value != null ? value.trim().replace('\\', '/') : "";
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        return path;
    }
}
