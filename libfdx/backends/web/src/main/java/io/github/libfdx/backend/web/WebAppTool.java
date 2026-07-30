package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Command-line entry point used by build integrations to generate a web application shell.
 *
 * @author xpenatan
 */
public final class WebAppTool {
    private WebAppTool() {
    }

    /**
     * Generates the web application described by the versioned request file.
     *
     * @param args the request-file path
     * @throws IOException if the request or generated files cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Properties request = loadRequest(args);
        WebAppWriter.write(
                WebApp.builder()
                        .webappDirectory(path(request, "webappDirectory"))
                        .title(required(request, "title"))
                        .width(integer(request, "width"))
                        .height(integer(request, "height"))
                        .canvasId(required(request, "canvasId"))
                        .entryPointName(required(request, "entryPointName"))
                        .mainClassArgs(required(request, "mainClassArgs"))
                        .targetFileName(required(request, "targetFileName"))
                        .wasm(Boolean.parseBoolean(required(request, "wasm")))
                        .assets(paths(request, "assets"))
                        .runtimeClasspath(paths(request, "runtimeClasspath"))
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
        int count = integer(request, name + ".count");
        ArrayList<Path> paths = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            paths.add(path(request, name + "." + index));
        }
        return List.copyOf(paths);
    }

    private static int integer(Properties request, String name) {
        return Integer.parseInt(required(request, name));
    }

    private static String required(Properties request, String name) {
        String value = request.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing libFDX tool request property: " + name);
        }
        return value;
    }
}
