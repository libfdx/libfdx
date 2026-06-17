package io.github.libfdx.tools.shader;

/**
 * Android JNI Tint compiler bridge.
 *
 * @author xpenatan
 */
public final class FdxTintAndroidJniCompilerBridge implements FdxTintCompilerBridge {
    private static volatile boolean loaded;

    @Override
    public FdxTintCompilerBridgeResult compile(FdxTintCompilerBridgeRequest request) {
        try {
            load();
            String encoded = compileNative(request.source(), FdxShaderTargets.nativeTarget(request.target()),
                    FdxShaderTargets.nativeStage(request.stage()), request.entryPoint(), request.glslProfile(),
                    request.glslEsProfile());
            return FdxTintNativeBridgeSupport.decodeBase64(encoded);
        } catch (UnsatisfiedLinkError error) {
            return FdxTintCompilerBridgeResult.failure("Could not load Android shader compiler JNI library: "
                    + error.getMessage());
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        synchronized (FdxTintAndroidJniCompilerBridge.class) {
            if (!loaded) {
                System.loadLibrary("fdx_shaderc");
                loaded = true;
            }
        }
    }

    private static native String compileNative(String source, int target, int stage, String entryPoint,
            String glslProfile, String glslEsProfile);
}
