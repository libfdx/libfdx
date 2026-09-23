package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;

/**
 * Renderable bindings for a borrowed canonical map and borrowed managed textures.
 * The manager owns this binding; callers release their asset lease/scope.
 * Disposing a binding never disposes its dependency resources.
 */
public final class TileMapAsset implements Disposable {
    private final io.github.libfdx.maps.TileMap map;
    private final TileSet tiles;
    private boolean disposed;
    TileMapAsset(io.github.libfdx.maps.TileMap map, TileSet tiles) { this.map = map; this.tiles = tiles; }
    public io.github.libfdx.maps.TileMap map() { check(); return map; }
    public TileSet tiles() { check(); return tiles; }
    @Override
    public void dispose() { if (!disposed) { disposed = true; tiles.clear(); } }
    @Override
    public boolean isDisposed() { return disposed; }
    private void check() { if (disposed) { throw new FdxException("TileMapAsset is disposed"); } }
}
