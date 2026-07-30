package io.github.libfdx.tools.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderValidationToolTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesDeterministicPassingReport() throws IOException {
        Path source = temporaryDirectory.resolve("shaders");
        Path report = temporaryDirectory.resolve("report.md");
        Files.createDirectories(source);
        Files.writeString(source.resolve("triangle.wgsl"), """
                @vertex
                fn vertex_main() -> @builtin(position) vec4f {
                    return vec4f(0.0, 0.0, 0.0, 1.0);
                }
                """, StandardCharsets.UTF_8);

        ShaderValidationTool.main(new String[]{request(source, report).toString()});

        String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains("status: PASS"));
        assertTrue(text.contains("## triangle.wgsl"));
    }

    @Test
    void failsAfterWritingReportForInvalidShader() throws IOException {
        Path source = temporaryDirectory.resolve("invalid-shaders");
        Path report = temporaryDirectory.resolve("invalid-report.md");
        Files.createDirectories(source);
        Files.writeString(source.resolve("invalid.wgsl"), "", StandardCharsets.UTF_8);

        assertThrows(
                IllegalStateException.class,
                () -> ShaderValidationTool.main(new String[]{request(source, report).toString()}));

        assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains("status: FAIL"));
    }

    private Path request(Path source, Path report) throws IOException {
        Path request = temporaryDirectory.resolve(report.getFileName() + ".properties");
        Properties properties = new Properties();
        properties.setProperty("formatVersion", "1");
        properties.setProperty("sourceDirectory", source.toString());
        properties.setProperty("defaultProfile", "webgpu");
        properties.setProperty("reportFile", report.toString());
        try (Writer writer = Files.newBufferedWriter(request, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
        return request;
    }
}
