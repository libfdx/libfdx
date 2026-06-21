package io.github.libfdx.backend.web;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.teavm.jso.JSBody;

/**
 * Web runtime shader compiler backed by the loaded fdx Emscripten module.
 *
 * @author xpenatan
 */
final class WebRuntimeShaderCompiler implements RuntimeShaderCompiler {
    /**
     * Compiles the request.
     *
     * @param request the request
     * @return the result
     */
    @Override
    public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
        String encoded = compileBase64(request.source(), nativeTarget(request.target()), nativeStage(request.stage()),
                request.entryPoint(), request.glslProfile(), request.glslEsProfile());
        if (encoded == null || encoded.length() == 0) {
            return failure("Web runtime shader compiler is not available. Regenerate fdx web native with "
                    + "libfdx.runtimeFdx.shaderCompiler=true or the default compiler-enabled web build.");
        }
        return decodeBase64(encoded);
    }

    /**
     * Returns whether the web compiler bridge is installed.
     *
     * @return true when installed
     */
    boolean available() {
        return isNativeCompilerAvailable();
    }

    private static RuntimeShaderCompileResult decodeBase64(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 16) {
            return failure("Native shader compiler returned a truncated result");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int status = buffer.getInt();
        RuntimeShaderCompileOutputKind kind = outputKind(buffer.getInt());
        int outputSize = Math.max(0, buffer.getInt());
        int diagnosticSize = Math.max(0, buffer.getInt());
        if (buffer.remaining() < outputSize + diagnosticSize) {
            return failure("Native shader compiler returned an incomplete result");
        }
        byte[] output = new byte[outputSize];
        buffer.get(output);
        byte[] diagnostics = new byte[diagnosticSize];
        buffer.get(diagnostics);
        if (status != 0) {
            return failure(new String(diagnostics, StandardCharsets.UTF_8));
        }
        if (kind == RuntimeShaderCompileOutputKind.TEXT) {
            return RuntimeShaderCompileResult.text(new String(output, StandardCharsets.UTF_8));
        }
        if (kind == RuntimeShaderCompileOutputKind.SPIRV) {
            return RuntimeShaderCompileResult.spirv(output);
        }
        return failure("Native shader compiler returned no output");
    }

    private static RuntimeShaderCompileResult failure(String message) {
        return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                RuntimeShaderCompileDiagnostic.of(message)
        });
    }

    private static int nativeTarget(RuntimeShaderCompileTarget target) {
        switch (target) {
            case WEBGPU_WGSL:
                return 0;
            case WGPU_WGSL:
                return 1;
            case WEBGL_GLSL_ES:
                return 2;
            case GLES_GLSL_ES:
                return 3;
            case OPENGL_GLSL:
                return 4;
            case VULKAN_SPIRV:
                return 5;
            case METAL_MSL:
                return 6;
            case DIRECTX_HLSL:
                return 7;
            default:
                return 0;
        }
    }

    private static int nativeStage(RuntimeShaderCompileStage stage) {
        if (stage == RuntimeShaderCompileStage.VERTEX) {
            return 1;
        }
        if (stage == RuntimeShaderCompileStage.FRAGMENT) {
            return 2;
        }
        return 0;
    }

    private static RuntimeShaderCompileOutputKind outputKind(int value) {
        if (value == 1) {
            return RuntimeShaderCompileOutputKind.TEXT;
        }
        if (value == 2) {
            return RuntimeShaderCompileOutputKind.SPIRV;
        }
        return RuntimeShaderCompileOutputKind.NONE;
    }

    @JSBody(params = { "source", "target", "stage", "entryPoint", "glslProfile", "glslEsProfile" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "if (!root.libfdxShaderCompileBase64) return '';\n" +
            "return root.libfdxShaderCompileBase64(source, target, stage, entryPoint, glslProfile, glslEsProfile);")
    private static native String compileBase64(String source, int target, int stage, String entryPoint,
            String glslProfile, String glslEsProfile);

    @JSBody(script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "return typeof root.libfdxShaderCompileBase64 === 'function';")
    private static native boolean isNativeCompilerAvailable();
}
