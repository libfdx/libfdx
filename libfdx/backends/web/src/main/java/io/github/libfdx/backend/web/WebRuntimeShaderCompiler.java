package io.github.libfdx.backend.web;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.internal.NativeRuntimeShaderResultEnvelope;
import org.teavm.jso.JSBody;
import io.github.libfdx.core.FdxFuture;
import java.util.function.Consumer;

/**
 * Web shader compiler backed by the loaded fdx Emscripten module.
 *
 * @author xpenatan
 */
final class WebRuntimeShaderCompiler implements RuntimeShaderCompiler {
    private final WebShaderWorker worker = new WebShaderWorker(this);
    private boolean disposed;

    /** Worker results complete on the browser event loop; unavailable-worker fallback uses execute. */
    @Override
    public FdxFuture<RuntimeShaderCompileResult> compileAsync(RuntimeShaderCompileRequest request,
            Consumer<Runnable> execute) {
        return worker.compile(request, execute);
    }

    void dispose() { disposed = true; worker.dispose(); }
    /** Fingerprinted from the compiler JS/Wasm during TeaVM compilation, without runtime I/O. */
    @Override
    public String cacheIdentity() {
        String identity = compiledIdentity();
        return identity == null || identity.isEmpty() ? null : identity;
    }

    // Replaced by WebTeaVMPlugin. Builds without compiler inputs disable persistent shader caching.
    private static String compiledIdentity() { return null; }

    /**
     * Compiles the request.
     *
     * @param request the request
     * @return the result
     */
    @Override
    public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
        if (disposed) throw new io.github.libfdx.core.FdxException("Shader compiler is disposed");
        if (!available()) {
            return failure("Web runtime shader compiler is not available. Regenerate fdx web native with "
                    + "libfdx.runtimeFdx.shaderCompiler=true or the default compiler-enabled web build.");
        }
        String encoded = compileBase64(request.source(), nativeTarget(request.target()), nativeStage(request.stage()),
                request.entryPoint(), request.glslProfile(), request.glslEsProfile());
        if (encoded == null || encoded.length() == 0) {
            return failure("Web runtime shader compiler is not available. Regenerate fdx web native with "
                    + "libfdx.runtimeFdx.shaderCompiler=true or the default compiler-enabled web build.");
        }
        return NativeRuntimeShaderResultEnvelope.decodeBase64(encoded);
    }

    /**
     * Returns whether the web compiler bridge is installed.
     *
     * @return true when installed
     */
    boolean available() {
        return isNativeCompilerAvailable();
    }

    private static RuntimeShaderCompileResult failure(String message) {
        return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                RuntimeShaderCompileDiagnostic.of(message)
        });
    }

    static int nativeTarget(RuntimeShaderCompileTarget target) {
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

    static int nativeStage(RuntimeShaderCompileStage stage) {
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
