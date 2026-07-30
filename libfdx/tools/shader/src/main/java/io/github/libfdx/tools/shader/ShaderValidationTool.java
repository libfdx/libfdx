package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderProfileValidator;
import io.github.libfdx.graphics.shader.ShaderValidationDiagnostic;
import io.github.libfdx.graphics.shader.ShaderValidationSeverity;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Command-line entry point used by build integrations to validate WGSL shader profiles.
 *
 * @author xpenatan
 */
public final class ShaderValidationTool {
    private static final String PROFILE_PREFIX = "@fdx.profile";

    private ShaderValidationTool() {
    }

    /**
     * Validates the shader directory described by the versioned request file.
     *
     * @param args the request-file path
     * @throws IOException if the request, shader sources, or report cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Properties request = loadRequest(args);
        Path root = path(request, "sourceDirectory");
        ShaderProfile profile = ShaderProfile.fromId(
                required(request, "defaultProfile"),
                ShaderProfile.PORTABLE_WEBGPU);
        List<ShaderValidationEntry> entries = validateDirectory(root, profile);
        Path output = path(request, "reportFile");
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, toMarkdown(root, entries), StandardCharsets.UTF_8);
        if (errorCount(entries) != 0) {
            throw new IllegalStateException("libFDX shader validation failed. See " + output.toAbsolutePath());
        }
        System.out.println("Validated " + entries.size() + " libfdx shader source file(s): "
                + output.toAbsolutePath());
    }

    private static List<ShaderValidationEntry> validateDirectory(Path sourceDirectory, ShaderProfile defaultProfile)
            throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            return List.of();
        }
        try (var stream = Files.walk(sourceDirectory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".wgsl"))
                    .sorted()
                    .map(path -> validateFile(path, defaultProfile))
                    .toList();
        }
    }

    private static ShaderValidationEntry validateFile(Path path, ShaderProfile defaultProfile) {
        try {
            String source = Files.readString(path);
            ShaderProfile profile = profileFromSource(source, defaultProfile);
            return new ShaderValidationEntry(path, profile.id(),
                    ShaderProfileValidator.validateWgsl(profile, source).diagnostics());
        } catch (IOException error) {
            throw new IllegalStateException("Could not read shader source: " + path, error);
        }
    }

    private static ShaderProfile profileFromSource(String source, ShaderProfile defaultProfile) {
        if (source == null || source.isEmpty()) {
            return defaultProfile;
        }
        return source.lines()
                .limit(32)
                .map(String::trim)
                .map(line -> line.startsWith("//") ? line.substring(2).trim() : line)
                .filter(line -> line.startsWith(PROFILE_PREFIX))
                .map(line -> line.substring(PROFILE_PREFIX.length()).trim())
                .map(value -> value.startsWith("=") ? value.substring(1).trim() : value)
                .map(value -> ShaderProfile.fromId(value, defaultProfile))
                .findFirst()
                .orElse(defaultProfile);
    }

    private static String toMarkdown(Path root, List<ShaderValidationEntry> entries) {
        StringBuilder output = new StringBuilder();
        int errors = errorCount(entries);
        output.append("# libFDX Shader Validation\n\n");
        output.append("status: ").append(errors == 0 ? "PASS" : "FAIL").append('\n');
        output.append("shaders: ").append(entries.size()).append('\n');
        output.append("errors: ").append(errors).append("\n\n");
        for (ShaderValidationEntry entry : entries) {
            output.append("## ").append(relative(root, entry.path())).append('\n');
            output.append("profile: ").append(entry.profileId()).append('\n');
            if (entry.diagnostics().length == 0) {
                output.append("result: PASS\n\n");
                continue;
            }
            output.append("result: FAIL\n");
            for (ShaderValidationDiagnostic diagnostic : entry.diagnostics()) {
                output.append("- ")
                        .append(diagnostic.severity())
                        .append(' ')
                        .append(diagnostic.code())
                        .append(": ")
                        .append(diagnostic.message())
                        .append('\n');
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static String relative(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException error) {
            return path.toString().replace('\\', '/');
        }
    }

    private static int errorCount(List<ShaderValidationEntry> entries) {
        int count = 0;
        for (ShaderValidationEntry entry : entries) {
            for (ShaderValidationDiagnostic diagnostic : entry.diagnostics()) {
                if (diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                    count++;
                }
            }
        }
        return count;
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

    private static String required(Properties request, String name) {
        String value = request.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing libFDX tool request property: " + name);
        }
        return value;
    }

    private record ShaderValidationEntry(
            Path path,
            String profileId,
            ShaderValidationDiagnostic[] diagnostics) {
    }
}
