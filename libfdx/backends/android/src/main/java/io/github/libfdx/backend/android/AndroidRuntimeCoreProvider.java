package io.github.libfdx.backend.android;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;

final class AndroidRuntimeCoreProvider implements RuntimeCoreProvider {
    private final FontRasterizer fontRasterizer = new AndroidFreeTypeFontRasterizer();

    @Override
    public FontRasterizer fontRasterizer() {
        return fontRasterizer;
    }

    @Override
    public boolean nativeFontRasterizerAvailable() {
        return true;
    }
}
