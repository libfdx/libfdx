package io.github.libfdx.backend.desktopc;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Command-line entry point used by build integrations to generate a desktop C project.
 *
 * @author xpenatan
 */
public final class NativeProjectTool {
    private NativeProjectTool() {
    }

    /**
     * Generates the native project described by the versioned request file.
     *
     * @param args the request-file path
     * @throws IOException if the request or generated files cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Properties request = loadRequest(args);
        NativeProjectWriter.write(
                NativeProject.builder()
                        .buildRoot(path(request, "buildRoot"))
                        .generatedSourcesDirectory(path(request, "generatedSourcesDirectory"))
                        .releaseDirectory(path(request, "releaseDirectory"))
                        .projectName(required(request, "projectName"))
                        .buildType(required(request, "buildType"))
                        .showConsole(Boolean.parseBoolean(required(request, "showConsole")))
                        .nativeResourceClasspath(paths(request, "nativeResourceClasspath"))
                        .build());
    }

    private static Properties loadRequest(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one libFDX tool request-file path.");
        }
        Properties request = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            request.load(reader);
        }
        if (!"1".equals(request.getProperty("formatVersion"))) {
            throw new IllegalArgumentException("Unsupported libFDX tool request format: "
                    + request.getProperty("formatVersion"));
        }
        return request;
    }

    private static Path path(Properties request, String name) {
        return Path.of(required(request, name));
    }

    private static List<Path> paths(Properties request, String name) {
        int count = Integer.parseInt(required(request, name + ".count"));
        ArrayList<Path> paths = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            paths.add(path(request, name + "." + index));
        }
        return List.copyOf(paths);
    }

    private static String required(Properties request, String name) {
        String value = request.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing libFDX tool request property: " + name);
        }
        return value;
    }
}
