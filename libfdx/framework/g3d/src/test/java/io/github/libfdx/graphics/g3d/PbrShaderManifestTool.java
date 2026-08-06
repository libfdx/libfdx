package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Regenerates the checked-in FDXI payloads for the built-in PBR shaders.
 */
public final class PbrShaderManifestTool {
    private static final int BASE64_CHUNK_LENGTH = 100;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: PbrShaderManifestTool <fdx_shaderc_reflect> <output.java> <generate|check>");
        }
        Path compiler = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path output = Path.of(arguments[1]).toAbsolutePath().normalize();
        boolean check = switch (arguments[2]) {
            case "generate" -> false;
            case "check" -> true;
            default -> throw new IllegalArgumentException("Unknown PBR manifest mode: " + arguments[2]);
        };
        if (!Files.isRegularFile(compiler)) {
            throw new IllegalStateException("FDXI reflection compiler does not exist: " + compiler);
        }

        String staticSource = PbrShaderProvider.PBR_RENDERER_TEMPLATE;
        String skinnedSource =
                PbrShaderProvider.skinnedPbrRendererTemplate();
        Path temporaryDirectory = Files.createTempDirectory("libfdx-pbr-manifest-");
        try {
            byte[] staticReflection = reflect(compiler, temporaryDirectory, "pbr-static", staticSource);
            byte[] skinnedReflection = reflect(compiler, temporaryDirectory, "pbr-skinned", skinnedSource);
            ShaderReflection freshStatic = decodeAndRequireSize(staticReflection, 1_232, "static");
            ShaderReflection freshSkinned = decodeAndRequireSize(skinnedReflection, 5_344, "skinned");
            String generated = generatedSource(staticSource, skinnedSource, staticReflection, skinnedReflection);
            if (check) {
                if (!Files.isRegularFile(output)) {
                    throw new IllegalStateException("Generated PBR manifest is missing: " + output);
                }
                String current = Files.readString(output, StandardCharsets.UTF_8);
                if (!current.equals(generated)) {
                    throw new IllegalStateException("Generated PBR manifest is stale. Run "
                            + ":libfdx:framework:g3d:generate_pbr_shader_manifest");
                }
                if (!freshStatic.physicallyEquivalent(BuiltInPbrShaderManifest.staticReflection())
                        || !freshSkinned.physicallyEquivalent(BuiltInPbrShaderManifest.skinnedReflection())) {
                    throw new IllegalStateException(
                            "Bundled PBR manifests differ structurally from fresh Tint reflection");
                }
            } else {
                writeAtomically(output, generated);
            }
        } finally {
            deleteIfExists(temporaryDirectory.resolve("pbr-static.wgsl"));
            deleteIfExists(temporaryDirectory.resolve("pbr-static.fdxi"));
            deleteIfExists(temporaryDirectory.resolve("pbr-skinned.wgsl"));
            deleteIfExists(temporaryDirectory.resolve("pbr-skinned.fdxi"));
            deleteIfExists(temporaryDirectory);
        }
    }

    private static ShaderReflection decodeAndRequireSize(byte[] fdxi, long expectedSize, String variant) {
        ShaderReflection reflection =
                ShaderReflection.fromRuntime(RuntimeShaderReflection.fromBytes(fdxi));
        long actualSize = reflection.requireBinding(1, 0).bufferLayout().minimumBindingSize();
        if (actualSize != expectedSize) {
            throw new IllegalStateException("Reflected " + variant + " PBR minimum binding size is "
                    + actualSize + ", expected " + expectedSize);
        }
        return reflection;
    }

    private static void writeAtomically(Path output, String generated) throws IOException {
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, generated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] reflect(Path compiler, Path directory, String name, String source)
            throws IOException, InterruptedException {
        Path wgsl = directory.resolve(name + ".wgsl");
        Path fdxi = directory.resolve(name + ".fdxi");
        Files.writeString(wgsl, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(compiler.toString(), wgsl.toString(), fdxi.toString())
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("FDXI reflection failed for " + name + " with exit code " + exitCode);
        }
        return Files.readAllBytes(fdxi);
    }

    private static String generatedSource(String staticSource, String skinnedSource,
            byte[] staticReflection, byte[] skinnedReflection) {
        StringBuilder source = new StringBuilder(48_000);
        source.append("package io.github.libfdx.graphics.internal;\n\n")
                .append("import java.util.Base64;\n\n")
                .append("/** Generated by ")
                .append(":libfdx:framework:g3d:generate_pbr_shader_manifest. */\n")
                .append("final class GeneratedPbrShaderManifestData {\n")
                .append("    static final String STATIC_SOURCE_SHA256 = \"")
                .append(sha256(staticSource))
                .append("\";\n")
                .append("    static final String SKINNED_SOURCE_SHA256 = \"")
                .append(sha256(skinnedSource))
                .append("\";\n\n");
        appendPayload(source, "STATIC_FDXI", staticReflection);
        appendPayload(source, "SKINNED_FDXI", skinnedReflection);
        source.append("    private GeneratedPbrShaderManifestData() {\n")
                .append("    }\n")
                .append("}\n");
        return source.toString();
    }

    private static void appendPayload(StringBuilder source, String name, byte[] payload) {
        String base64 = Base64.getEncoder().encodeToString(payload);
        source.append("    static byte[] ").append(toMethodName(name)).append("() {\n")
                .append("        return Base64.getDecoder().decode(\n");
        for (int offset = 0; offset < base64.length(); offset += BASE64_CHUNK_LENGTH) {
            int end = Math.min(offset + BASE64_CHUNK_LENGTH, base64.length());
            source.append("                \"").append(base64, offset, end).append("\"");
            if (end < base64.length()) {
                source.append(" +");
            }
            source.append('\n');
        }
        source.append("        );\n")
                .append("    }\n\n");
    }

    private static String toMethodName(String constantName) {
        return switch (constantName) {
            case "STATIC_FDXI" -> "staticFdxi";
            case "SKINNED_FDXI" -> "skinnedFdxi";
            default -> throw new IllegalArgumentException("Unknown FDXI payload: " + constantName);
        };
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup after generation or validation.
        }
    }

    private PbrShaderManifestTool() {
    }
}
