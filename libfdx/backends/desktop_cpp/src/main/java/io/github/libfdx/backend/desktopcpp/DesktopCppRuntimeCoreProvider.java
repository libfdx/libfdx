package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Provides desktop C++ runtime core services.
 *
 * @author xpenatan
 */
final class DesktopCppRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new DesktopCppFreeTypeFontRasterizer();
    private final DesktopCppRuntimeShaderCompiler shaderCompiler =
            new DesktopCppRuntimeShaderCompiler();

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
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }

    /**
     * Returns the runtime shader compiler.
     *
     * @return the runtime shader compiler
     */
    @Override
    public RuntimeShaderCompiler shaderCompiler() {
        return shaderCompiler;
    }

    /**
     * Returns whether the native shader compiler can be loaded.
     *
     * @return true when available
     */
    @Override
    public boolean nativeShaderCompilerAvailable() {
        return shaderCompiler.available();
    }
}
