package io.github.libfdx.runtime.core;

/**
 * Represents a runtime core.
 *
 * @author xpenatan
 */
public final class RuntimeCore {
    private static RuntimeCoreProvider provider = new DefaultRuntimeCoreProvider();

    private RuntimeCore() {
    }

    /**
     * Registers the provider.
     *
     * @param runtimeCoreProvider the runtime core provider
     */
    public static synchronized void registerProvider(RuntimeCoreProvider runtimeCoreProvider) {
        provider = runtimeCoreProvider != null ? runtimeCoreProvider : new DefaultRuntimeCoreProvider();
    }

    /**
     * Returns the provider.
     *
     * @return the provider
     */
    public static synchronized RuntimeCoreProvider provider() {
        return provider;
    }

    /**
     * Returns the font rasterizer.
     *
     * @return the font rasterizer
     */
    public static FontRasterizer fontRasterizer() {
        RuntimeCoreProvider current = provider();
        FontRasterizer rasterizer = current != null ? current.fontRasterizer() : null;
        if (rasterizer == null) {
            throw new RuntimeCoreException("Runtime core font rasterizer is not available");
        }
        return rasterizer;
    }

    /**
     * Returns the native font rasterizer available.
     *
     * @return true if native font rasterizer available succeeds or is active; false otherwise
     */
    public static boolean nativeFontRasterizerAvailable() {
        RuntimeCoreProvider current = provider();
        return current != null && current.nativeFontRasterizerAvailable();
    }
}