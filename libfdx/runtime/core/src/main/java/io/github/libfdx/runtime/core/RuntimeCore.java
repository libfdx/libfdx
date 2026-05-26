package io.github.libfdx.runtime.core;

public final class RuntimeCore {
    private static RuntimeCoreProvider provider = new DefaultRuntimeCoreProvider();

    private RuntimeCore() {
    }

    public static synchronized void registerProvider(RuntimeCoreProvider runtimeCoreProvider) {
        provider = runtimeCoreProvider != null ? runtimeCoreProvider : new DefaultRuntimeCoreProvider();
    }

    public static synchronized RuntimeCoreProvider provider() {
        return provider;
    }

    public static FontRasterizer fontRasterizer() {
        RuntimeCoreProvider current = provider();
        FontRasterizer rasterizer = current != null ? current.fontRasterizer() : null;
        if (rasterizer == null) {
            throw new RuntimeCoreException("Runtime core font rasterizer is not available");
        }
        return rasterizer;
    }

    public static boolean nativeFontRasterizerAvailable() {
        RuntimeCoreProvider current = provider();
        return current != null && current.nativeFontRasterizerAvailable();
    }
}