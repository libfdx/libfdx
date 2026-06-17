package io.github.libfdx.tools.shader;

/**
 * Creates runtime/editor shader compilers.
 *
 * @author xpenatan
 */
public final class FdxRuntimeShaderCompiler {
    private FdxRuntimeShaderCompiler() {
    }

    /**
     * Creates a Tint-backed compiler.
     *
     * @param bridge the bridge
     * @return the compiler
     */
    public static FdxShaderCompiler tint(FdxTintCompilerBridge bridge) {
        return new FdxTintNativeShaderCompiler(bridge);
    }

    /**
     * Creates a WGSL passthrough compiler.
     *
     * @return the compiler
     */
    public static FdxShaderCompiler passthrough() {
        return new FdxPassthroughShaderCompiler();
    }
}
