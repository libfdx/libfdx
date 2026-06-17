package io.github.libfdx.backend.android;

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

/**
 * Android runtime shader compiler backed by the fdx native library.
 *
 * @author xpenatan
 */
final class AndroidRuntimeShaderCompiler implements RuntimeShaderCompiler {
    /**
     * Returns whether the native compiler symbol is available.
     *
     * @return true when available
     */
    boolean available() {
        try {
            return AndroidRuntimeCoreNative.load() && isAvailableNative();
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    @Override
    public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
        try {
            if (!AndroidRuntimeCoreNative.load()) {
                return failure("Could not load Android runtime fdx native library: "
                        + AndroidRuntimeCoreNative.failureMessage());
            }
            String encoded = compileNative(request.source(), nativeTarget(request.target()),
                    nativeStage(request.stage()), request.entryPoint(), request.glslProfile(),
                    request.glslEsProfile());
            return decodeBase64(encoded);
        } catch (UnsatisfiedLinkError error) {
            return failure("Could not run Android runtime shader compiler: " + error.getMessage());
        }
    }

    private static RuntimeShaderCompileResult decodeBase64(String encoded) {
        if (encoded == null || encoded.length() == 0) {
            return failure("Native shader compiler returned no result");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int status = buffer.getInt();
        RuntimeShaderCompileOutputKind kind = outputKind(buffer.getInt());
        int outputSize = buffer.getInt();
        int diagnosticSize = buffer.getInt();
        byte[] output = new byte[Math.max(0, outputSize)];
        buffer.get(output);
        byte[] diagnostics = new byte[Math.max(0, diagnosticSize)];
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

    private static native String compileNative(String source, int target, int stage, String entryPoint,
            String glslProfile, String glslEsProfile);

    private static native boolean isAvailableNative();
}
