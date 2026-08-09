package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies G3D's explicit PBR shader interface against fresh Tint reflection.
 */
public final class PbrShaderInterfaceTool {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: PbrShaderInterfaceTool <fdx_shaderc_reflect>");
        }
        Path compiler = Path.of(arguments[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(compiler)) {
            throw new IllegalStateException(
                    "Shader reflection compiler does not exist: " + compiler);
        }

        Path temporaryDirectory = Files.createTempDirectory("libfdx-pbr-interface-");
        try {
            validate(compiler, temporaryDirectory, "static",
                    PbrShaderProvider.PBR_RENDERER_TEMPLATE,
                    PbrShaderParameters.staticReflection());
            validate(compiler, temporaryDirectory, "skinned",
                    PbrShaderProvider.skinnedPbrRendererTemplate(),
                    PbrShaderParameters.skinnedReflection());
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private static void validate(Path compiler, Path directory, String variant,
            String source, ShaderReflection expected) throws Exception {
        Path wgsl = directory.resolve(variant + ".wgsl");
        Path binary = directory.resolve(variant + "-interface.bin");
        Files.writeString(wgsl, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(compiler.toString(), wgsl.toString(),
                binary.toString())
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.isRegularFile(binary) || Files.size(binary) == 0) {
            throw new IllegalStateException("Tint reflection failed for the " + variant
                    + " PBR shader with exit code " + exitCode);
        }
        ShaderReflection actual = ShaderReflection.fromRuntime(
                RuntimeShaderReflection.fromBytes(Files.readAllBytes(binary)));
        if (!expected.physicallyEquivalent(actual)) {
            throw new IllegalStateException("The explicit " + variant
                    + " PBR shader interface is out of sync with WGSL"
                    + "\nJava interface: " + expected.physicalHash()
                    + "\nTint reflection: " + actual.physicalHash());
        }
    }

    private static void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup of temporary verification files.
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Best-effort cleanup of the temporary verification directory.
        }
    }

    private PbrShaderInterfaceTool() {
    }
}
