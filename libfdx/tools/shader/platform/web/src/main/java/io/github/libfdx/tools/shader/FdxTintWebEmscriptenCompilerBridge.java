package io.github.libfdx.tools.shader;

import org.teavm.jso.JSBody;

/**
 * Web Emscripten/Wasm Tint compiler bridge.
 *
 * @author xpenatan
 */
public final class FdxTintWebEmscriptenCompilerBridge implements FdxTintCompilerBridge {
    @Override
    public FdxTintCompilerBridgeResult compile(FdxTintCompilerBridgeRequest request) {
        String encoded = compileBase64(request.source(), FdxShaderTargets.nativeTarget(request.target()),
                FdxShaderTargets.nativeStage(request.stage()), request.entryPoint(), request.glslProfile(),
                request.glslEsProfile());
        if (encoded == null || encoded.length() == 0) {
            return FdxTintCompilerBridgeResult.failure("Web shader compiler is not installed. Load "
                    + "libfdx_shaderc.js and libfdx_shaderc_bridge.js before compiling shaders.");
        }
        return FdxTintNativeBridgeSupport.decodeBase64(encoded);
    }

    @JSBody(params = { "source", "target", "stage", "entryPoint", "glslProfile", "glslEsProfile" }, script =
            "if (typeof globalThis === 'undefined' || !globalThis.LibFdxShaderc) return '';\n"
            + "return globalThis.LibFdxShaderc.compileBase64(source, target, stage, entryPoint, glslProfile, "
            + "glslEsProfile);")
    private static native String compileBase64(String source, int target, int stage, String entryPoint,
            String glslProfile, String glslEsProfile);
}
