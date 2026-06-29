package io.github.libfdx.runtime.core;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Represents a runtime core.
 *
 * @author xpenatan
 */
public final class RuntimeCore {
    private static RuntimeCoreProvider provider = new DefaultRuntimeCoreProvider();

    private RuntimeCore() {
    }

    /**
     * Registers the provider.
     *
     * @param runtimeCoreProvider the runtime core provider
     */
    public static synchronized void registerProvider(RuntimeCoreProvider runtimeCoreProvider) {
        provider = runtimeCoreProvider != null ? runtimeCoreProvider : new DefaultRuntimeCoreProvider();
    }

    /**
     * Returns the provider.
     *
     * @return the provider
     */
    public static synchronized RuntimeCoreProvider provider() {
        return provider;
    }

    /**
     * Returns the font rasterizer.
     *
     * @return the font rasterizer
     */
    public static FontRasterizer fontRasterizer() {
        RuntimeCoreProvider current = provider();
        FontRasterizer rasterizer = current != null ? current.fontRasterizer() : null;
        if (rasterizer == null) {
            throw new RuntimeCoreException("Runtime core font rasterizer is not available");
        }
        return rasterizer;
    }

    /**
     * Returns the runtime shader compiler.
     *
     * @return the runtime shader compiler
     */
    public static RuntimeShaderCompiler shaderCompiler() {
        RuntimeCoreProvider current = provider();
        RuntimeShaderCompiler compiler = current != null ? current.shaderCompiler() : null;
        if (compiler == null) {
            throw new RuntimeCoreException("Runtime core shader compiler is not available");
        }
        return compiler;
    }

    /**
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    public static boolean nativeFontRasterizerAvailable() {
        RuntimeCoreProvider current = provider();
        return current != null && current.nativeFontRasterizerAvailable();
    }

    /**
     * Returns whether the native shader compiler is available.
     *
     * @return true if native shader compiler is available; false otherwise
     */
    public static boolean nativeShaderCompilerAvailable() {
        RuntimeCoreProvider current = provider();
        return current != null && current.nativeShaderCompilerAvailable();
    }
}
