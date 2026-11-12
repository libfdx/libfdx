package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cumulative successful preview-only outputs; never alters Gradle's files. */
final class PreviewJavaOutputs {
    private final Path directory;
    private final Map<PreviewJavaCompiler.Module, Path> modules = new LinkedHashMap<>();

    PreviewJavaOutputs(Path directory) {
        this.directory = directory;
    }

    void accept(PreviewJavaCompiler.Result result) throws IOException {
        for (var entry : result.outputs().entrySet()) {
            Path destination = directory.resolve("module-" + entry.getKey().index());
            copy(entry.getValue(), destination);
            modules.put(entry.getKey(), destination);
        }
    }

    List<String> classpath() {
        return modules.values().stream().map(Path::toString).toList();
    }

    List<String> snapshot(Path target, boolean applicationOnly) throws IOException {
        List<String> result = new ArrayList<>();
        for (var entry : modules.entrySet()) {
            if (applicationOnly && !entry.getKey().reloadable()) continue;
            Path destination = target.resolve(entry.getValue().getFileName());
            copy(entry.getValue(), destination);
            result.add(destination.toString());
        }
        return result;
    }

    List<String> applicationPaths(Path snapshot) {
        return modules.entrySet().stream().filter(entry -> entry.getKey().reloadable())
                .map(entry -> snapshot.resolve(entry.getValue().getFileName()).toString()).toList();
    }

    void clear() throws IOException {
        modules.clear();
        PreviewSnapshot.deleteOwned(directory.getParent(), directory);
    }

    private void copy(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
