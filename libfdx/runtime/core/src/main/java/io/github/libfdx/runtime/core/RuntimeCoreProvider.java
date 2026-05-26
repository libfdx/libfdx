package io.github.libfdx.runtime.core;

public interface RuntimeCoreProvider {
    FontRasterizer fontRasterizer();
    boolean nativeFontRasterizerAvailable();
}