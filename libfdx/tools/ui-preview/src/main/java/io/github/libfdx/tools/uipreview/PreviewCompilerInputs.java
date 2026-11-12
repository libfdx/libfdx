package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps javac's cached archive handles away from mutable Gradle outputs. */
final class PreviewCompilerInputs implements AutoCloseable {
    private final Path directory;
    private final Path cache;
    private final Set<String> runtime;
    private final Map<String, String> copies = new HashMap<>();

    PreviewCompilerInputs(PreviewLaunch launch) {
        directory = launch.session.resolve("compiler-inputs-" + java.util.UUID.randomUUID());
        String cachePath = launch.values.getProperty("cacheDirectory");
        cache = cachePath == null ? null : Path.of(cachePath).toAbsolutePath().normalize();
        runtime = Set.copyOf(launch.strings("classpath"));
    }

    List<String> snapshot(List<String> paths) throws IOException {
        List<String> result = new ArrayList<>();
        for (String value : paths) {
            if (runtime.contains(value)) continue; // Supplied through the worker's immutable runtime snapshot.
            Path source = Path.of(value).toAbsolutePath().normalize();
            if (!Files.exists(source)) continue;
            if (cache != null && source.startsWith(cache)) { result.add(source.toString()); continue; }
            if (!copies.containsKey(value)) {
                if (directory.startsWith(source)) throw new IOException("Compiler input contains the preview session: " + source);
                Path target = directory.resolve("input-" + copies.size() + (Files.isDirectory(source) ? "" : ".jar"));
                if (Files.isDirectory(source)) {
                    try (var files = Files.walk(source)) {
                        for (Path file : files.toList()) {
                            Path destination = target.resolve(source.relativize(file));
                            if (Files.isDirectory(file)) Files.createDirectories(destination);
                            else Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    Files.createDirectories(directory);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copies.put(value, target.toString());
            }
            result.add(copies.get(value));
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        PreviewSnapshot.deleteOwned(directory.getParent(), directory);
    }

}
