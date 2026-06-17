package io.github.libfdx.backend.android;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Provides android runtime core services.
 *
 * @author xpenatan
 */
final class AndroidRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new AndroidFreeTypeFontRasterizer();
    private final AndroidRuntimeShaderCompiler shaderCompiler = new AndroidRuntimeShaderCompiler();

    /**
     * Returns the font rasterizer.
     *
     * @return the font rasterizer
     */
    @Override
    public FontRasterizer fontRasterizer() {
        return fontRasterizer;
    }

    /**
     * Returns the runtime shader compiler.
     *
     * @return the runtime shader compiler
     */
    @Override
    public RuntimeShaderCompiler shaderCompiler() {
        return shaderCompiler.available() ? shaderCompiler : null;
    }

    /**
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }

    /**
     * Returns whether the native shader compiler is available.
     *
     * @return true if native shader compiler is available; false otherwise
     */
    @Override
    public boolean nativeShaderCompilerAvailable() {
        return shaderCompiler.available();
    }
}
