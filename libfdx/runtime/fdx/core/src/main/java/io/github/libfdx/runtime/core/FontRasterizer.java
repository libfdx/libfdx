package io.github.libfdx.runtime.core;

/**
 * Defines the contract for font rasterizer implementations.
 *
 * @author xpenatan
 */
public interface FontRasterizer {
    /**
     * Runs the rasterize step.
     *
     * @param fontBytes the font bytes
     * @param options the options
     * @return the rasterize
     */
    RasterizedFont rasterize(byte[] fontBytes, FontRasterizerOptions options);
}