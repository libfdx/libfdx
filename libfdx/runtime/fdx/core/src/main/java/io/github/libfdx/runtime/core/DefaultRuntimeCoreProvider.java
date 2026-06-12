package io.github.libfdx.runtime.core;

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
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    @Override
    public boolean nativeFontRasterizerAvailable() {
        return false;
    }
}
