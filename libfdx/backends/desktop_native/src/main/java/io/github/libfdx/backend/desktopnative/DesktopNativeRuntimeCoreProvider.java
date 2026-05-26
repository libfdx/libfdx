package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

final class DesktopNativeRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new DesktopNativeFreeTypeFontRasterizer();

    @Override
    public FontRasterizer fontRasterizer() {
        return fontRasterizer;
    }

    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }
}
