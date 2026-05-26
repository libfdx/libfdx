package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.internal.MathAcceleration;
import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

final class DesktopRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new DesktopFreeTypeFontRasterizer();

    DesktopRuntimeCoreProvider() {
        MathAcceleration.register(new DesktopNativeMathAccelerator());
    }

    @Override
    public FontRasterizer fontRasterizer() {
        return fontRasterizer;
    }

    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }
}
