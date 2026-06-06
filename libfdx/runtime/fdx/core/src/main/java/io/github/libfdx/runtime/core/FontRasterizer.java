package io.github.libfdx.runtime.core;

public interface FontRasterizer {
    RasterizedFont rasterize(byte[] fontBytes, FontRasterizerOptions options);
}