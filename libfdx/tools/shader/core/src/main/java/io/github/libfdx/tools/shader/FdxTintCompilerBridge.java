package io.github.libfdx.tools.shader;

/**
 * Low-level bridge implemented by Tint process/native backends.
 *
 * @author xpenatan
 */
public interface FdxTintCompilerBridge {
    /**
     * Compiles a bridge request.
     *
     * @param request the request
     * @return the result
     */
    FdxTintCompilerBridgeResult compile(FdxTintCompilerBridgeRequest request);
}
