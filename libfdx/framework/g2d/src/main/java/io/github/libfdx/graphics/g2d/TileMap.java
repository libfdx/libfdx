package io.github.libfdx.graphics.g2d;

/**
 * Compatibility entry point for generated tile-only maps.
 * The canonical CPU model is {@link io.github.libfdx.maps.TileMap}; new importers use it directly.
 */
@Deprecated
public final class TileMap extends io.github.libfdx.maps.TileMap {
    public TileMap(int width, int height, float tileWidth, float tileHeight) {
        super(width, height, tileWidth, tileHeight);
    }
    @Override public TileLayer addLayer() {
        TileLayer layer = new TileLayer(width(), height()); addLayer(layer); return layer;
    }
    public TileMap addLayer(TileLayer layer) { super.addLayer(layer); return this; }
    @Override public TileMap addLayer(io.github.libfdx.maps.TileLayer layer) {
        if (!(layer instanceof TileLayer)) {
            throw new io.github.libfdx.core.FdxException("Use maps.TileMap for canonical map layers");
        }
        super.addLayer(layer); return this;
    }
    @Override public TileLayer layer(int index) { return (TileLayer)super.layer(index); }
    @Override public TileLayer removeLayer(int index) { return (TileLayer)super.removeLayer(index); }
}
