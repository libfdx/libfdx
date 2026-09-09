package io.github.libfdx.graphics.g2d;

/** Compatibility entry point; cell data is owned by {@link io.github.libfdx.maps.TileLayer}. */
@Deprecated
public final class TileLayer extends io.github.libfdx.maps.TileLayer {
    public TileLayer(int width, int height) { super(width, height); }
    @Override public TileLayer tile(int x, int y, int tileId) { super.tile(x, y, tileId); return this; }
    @Override public TileLayer tile(int x, int y, int tileId, int transform) { super.tile(x, y, tileId, transform); return this; }
    @Override public TileLayer fill(int tileId) { super.fill(tileId); return this; }
    @Override public TileLayer visible(boolean visible) { super.visible(visible); return this; }
}
