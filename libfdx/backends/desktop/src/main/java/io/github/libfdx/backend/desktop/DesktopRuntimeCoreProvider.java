package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.internal.MathAcceleration;
import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

/**
 * Provides desktop runtime core services.
 *
 * @author xpenatan
 */
final class DesktopRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new DesktopFreeTypeFontRasterizer();

    DesktopRuntimeCoreProvider() {
        MathAcceleration.register(new DesktopNativeMathAccelerator());
    }

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
