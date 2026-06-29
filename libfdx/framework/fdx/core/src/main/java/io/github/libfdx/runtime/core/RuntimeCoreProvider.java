package io.github.libfdx.runtime.core;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Defines the provider contract for runtime core services.
 *
 * @author xpenatan
 */
public interface RuntimeCoreProvider {
    /**
     * Returns the font rasterizer.
     *
     * @return the font rasterizer
     */
    FontRasterizer fontRasterizer();

    /**
     * Returns the runtime shader compiler.
     *
     * @return the runtime shader compiler
     */
    default RuntimeShaderCompiler shaderCompiler() {
        return null;
    }

    /**
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    boolean nativeFontRasterizerAvailable();

    /**
     * Returns whether the native shader compiler is available.
     *
     * @return true if native shader compiler is available; false otherwise
     */
    default boolean nativeShaderCompilerAvailable() {
        return false;
    }
}
