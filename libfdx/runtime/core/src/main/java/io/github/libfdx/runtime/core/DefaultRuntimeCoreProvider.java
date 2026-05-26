package io.github.libfdx.runtime.core;

final class DefaultRuntimeCoreProvider implements RuntimeCoreProvider {
    @Override
    public FontRasterizer fontRasterizer() {
        return null;
    }

    @Override
    public boolean nativeFontRasterizerAvailable() {
        return false;
    }
}
