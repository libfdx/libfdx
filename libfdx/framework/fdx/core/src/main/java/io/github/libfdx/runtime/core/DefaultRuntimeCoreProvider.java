package io.github.libfdx.runtime.core;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Provides default runtime core services.
 *
 * @author xpenatan
 */
final class DefaultRuntimeCoreProvider implements RuntimeCoreProvider {
    /**
     * Returns the font rasterizer.
     *
     * @return the font rasterizer
     */
    @Override
    public FontRasterizer fontRasterizer() {
        return null;
    }

    /**
     * Returns the runtime shader compiler.
     *
     * @return the runtime shader compiler
     */
    @Override
    public RuntimeShaderCompiler shaderCompiler() {
        return null;
    }

    /**
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    @Override
    public boolean nativeFontRasterizerAvailable() {
        return false;
    }

    /**
     * Returns whether the native shader compiler is available.
     *
     * @return true if native shader compiler is available; false otherwise
     */
    @Override
    public boolean nativeShaderCompilerAvailable() {
        return false;
    }
}
