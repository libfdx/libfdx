package io.github.libfdx.backend.web;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

final class WebRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new WebFreeTypeFontRasterizer();

    @Override
    public FontRasterizer fontRasterizer() {
        return fontRasterizer;
    }

    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }
}
