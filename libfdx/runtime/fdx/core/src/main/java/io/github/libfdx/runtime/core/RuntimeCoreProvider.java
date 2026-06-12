package io.github.libfdx.runtime.core;

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
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    boolean nativeFontRasterizerAvailable();
}