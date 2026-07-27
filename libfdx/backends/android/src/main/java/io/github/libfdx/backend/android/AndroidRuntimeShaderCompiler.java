package io.github.libfdx.backend.android;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.internal.NativeRuntimeShaderResultEnvelope;

import java.nio.charset.StandardCharsets;

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
            String encoded = compileNative(request.source().getBytes(StandardCharsets.UTF_8),
                    nativeTarget(request.target()),
                    nativeStage(request.stage()), request.entryPoint(), request.glslProfile(),
                    request.glslEsProfile());
            return NativeRuntimeShaderResultEnvelope.decodeBase64(encoded);
        } catch (UnsatisfiedLinkError error) {
            return failure("Could not run Android runtime shader compiler: " + error.getMessage());
        }
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
        if (stage == RuntimeShaderCompileStage.COMPUTE) {
            return 3;
        }
        return 0;
    }

    private static native String compileNative(byte[] source, int target, int stage, String entryPoint,
            String glslProfile, String glslEsProfile);

    private static native boolean isAvailableNative();
}
