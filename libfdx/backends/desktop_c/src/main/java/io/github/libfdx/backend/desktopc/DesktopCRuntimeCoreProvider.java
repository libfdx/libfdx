package io.github.libfdx.backend.desktopc;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

/**
 * Provides desktop C runtime core services.
 *
 * @author xpenatan
 */
final class DesktopCRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new DesktopCFreeTypeFontRasterizer();

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
}
