package io.github.libfdx.backend.cppshared;

import com.github.xpenatan.jnative.NativeBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads native source files contributed by C++ backend and provider dependencies. */
public final class NativeResources {
    private static final String MANIFEST = "META-INF/libfdx/native-cpp.list";

    private NativeResources() {
    }

    /**
     * Extracts native resources below the application's build directory and registers them.
     * Resource manifests are discovered in both class directories and dependency JARs.
     *
     * @param builder the compiler builder receiving the source files
     * @param buildRoot the consuming module's build directory
     * @param loader the class loader containing backend and provider resources
     * @return the extracted files
     * @throws IOException if a resource is missing, invalid, or cannot be written
     */
    public static List<Path> register(NativeBuilder builder, Path buildRoot, ClassLoader loader)
            throws IOException {
        Objects.requireNonNull(builder, "builder");
        Path directory = Objects.requireNonNull(buildRoot, "buildRoot")
                .toAbsolutePath().normalize().resolve("native-inputs");
        Objects.requireNonNull(loader, "loader");
        Map<String, byte[]> resources = new LinkedHashMap<>();
        var manifests = loader.getResources(MANIFEST);
        while (manifests.hasMoreElements()) {
            URL manifest = manifests.nextElement();
            try (var reader = new BufferedReader(new InputStreamReader(
                    openResource(manifest), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String resource = line.trim();
                    if (resource.isEmpty() || resource.startsWith("#")) {
                        continue;
                    }
                    if (!resource.startsWith("libfdx-native/") || resource.contains("..")
                            || resource.contains("\\") || resource.contains(":")
                            || resource.contains("?") || resource.contains("#")
                            || resource.endsWith("/")) {
                        throw new IOException("Invalid native resource in " + manifest + ": " + resource);
                    }
                    String name = resource.substring(resource.lastIndexOf('/') + 1);
                    if (resources.containsKey(name)) {
                        throw new IOException("Duplicate native resource file name: " + name);
                    }
                    // Resolve within the same class directory or JAR as this manifest.
                    URL source = resourceUrl(manifest, resource);
                    try (var stream = openResource(source)) {
                        resources.put(name, stream.readAllBytes());
                    }
                }
            }
        }
        if (resources.isEmpty()) {
            throw new IOException("No C++ native resources found on the classpath");
        }
        Files.createDirectories(directory);
        var files = new java.util.ArrayList<Path>();
        for (var resource : resources.entrySet()) {
            Path file = directory.resolve(resource.getKey());
            Files.write(file, resource.getValue());
            files.add(file);
            builder.nativeFile(file);
        }
        return List.copyOf(files);
    }

    private static URL resourceUrl(URL manifest, String resource) throws IOException {
        String location = manifest.toExternalForm();
        return java.net.URI.create(location.substring(0, location.length() - MANIFEST.length())
                + resource).toURL();
    }

    private static InputStream openResource(URL resource) throws IOException {
        var connection = resource.openConnection();
        // Avoid keeping dependency JARs locked after extraction on Windows.
        connection.setUseCaches(false);
        return connection.getInputStream();
    }
}
