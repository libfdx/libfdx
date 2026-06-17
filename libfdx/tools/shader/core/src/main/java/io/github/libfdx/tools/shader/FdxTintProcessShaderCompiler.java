package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the native Tint compiler executable.
 *
 * @author xpenatan
 */
public final class FdxTintProcessShaderCompiler implements FdxTintCompilerBridge {
    private final Path executable;

    public FdxTintProcessShaderCompiler(Path executable) {
        if (executable == null) {
            throw new FdxException("Shader compiler executable cannot be null");
        }
        this.executable = executable;
    }

    @Override
    public FdxTintCompilerBridgeResult compile(FdxTintCompilerBridgeRequest request) {
        if (!Files.isRegularFile(executable)) {
            return FdxTintCompilerBridgeResult.failure("Shader compiler executable does not exist: " + executable);
        }
        Path source = null;
        try {
            source = Files.createTempFile("libfdx-shaderc-", ".wgsl");
            Files.writeString(source, request.source(), StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>();
            command.add(executable.toAbsolutePath().toString());
            command.add("--input");
            command.add(source.toAbsolutePath().toString());
            command.add("--target");
            command.add(targetName(request));
            command.add("--stage");
            command.add(stageName(request.stage()));
            command.add("--entry");
            command.add(request.entryPoint());
            command.add("--glsl-profile");
            command.add(request.glslProfile());
            command.add("--glsl-es-profile");
            command.add(request.glslEsProfile());
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            String text = new String(output, StandardCharsets.UTF_8).trim();
            if (exitCode != 0) {
                return FdxTintCompilerBridgeResult.failure(text);
            }
            return FdxTintNativeBridgeSupport.decodeBase64(text);
        } catch (IOException exception) {
            return FdxTintCompilerBridgeResult.failure("Could not run shader compiler executable: "
                    + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FdxTintCompilerBridgeResult.failure("Interrupted while running shader compiler executable");
        } finally {
            if (source != null) {
                try {
                    Files.deleteIfExists(source);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String targetName(FdxTintCompilerBridgeRequest request) {
        switch (request.target()) {
            case WEBGPU_WGSL:
                return "webgpu-wgsl";
            case WGPU_WGSL:
                return "wgpu-wgsl";
            case WEBGL_GLSL_ES:
                return "webgl-glsl-es";
            case GLES_GLSL_ES:
                return "gles-glsl-es";
            case OPENGL_GLSL:
                return "opengl-glsl";
            case VULKAN_SPIRV:
                return "vulkan-spirv";
            case METAL_MSL:
                return "metal-msl";
            case DIRECTX_HLSL:
                return "directx-hlsl";
            default:
                return "webgpu-wgsl";
        }
    }

    private static String stageName(FdxTintShaderStage stage) {
        if (stage == FdxTintShaderStage.VERTEX) {
            return "vertex";
        }
        if (stage == FdxTintShaderStage.FRAGMENT) {
            return "fragment";
        }
        return "module";
    }
}
