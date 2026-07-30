package io.github.libfdx.tools.font;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Command-line entry point used by build integrations to generate a bitmap font.
 *
 * @author xpenatan
 */
public final class BitmapFontTool {
    private BitmapFontTool() {
    }

    /**
     * Generates a bitmap font from the versioned request file.
     *
     * @param args the request-file path
     * @throws IOException if the request or generated files cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Properties request = loadRequest(args);
        BitmapFontResult result = BitmapFontGenerator.generate(
                BitmapFontSpec.builder()
                        .sourceFile(path(request, "sourceFile"))
                        .outputDirectory(path(request, "outputDirectory"))
                        .name(required(request, "name"))
                        .assetPath(required(request, "assetPath"))
                        .size(integer(request, "size"))
                        .padding(integer(request, "padding"))
                        .maxTextureSize(integer(request, "maxTextureSize"))
                        .characters(required(request, "characters"))
                        .build());
        System.out.println("Generated libfdx bitmap font " + result.assetFontPath());
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
