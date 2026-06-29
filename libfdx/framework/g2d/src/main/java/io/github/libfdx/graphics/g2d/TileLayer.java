package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;

/**
 * Stores tile ids for one 2D map layer.
 *
 * @author xpenatan
 */
public final class TileLayer {
    private final int width;
    private final int height;
    private final IntArray tiles;
    private boolean visible = true;

    /**
     * Creates a tile layer.
     *
     * @param width the width in tiles
     * @param height the height in tiles
     */
    public TileLayer(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("TileLayer size must be greater than zero");
        }
        int size = cellCount(width, height);
        this.width = width;
        this.height = height;
        this.tiles = new IntArray(size);
        for (int i = 0; i < size; i++) {
            tiles.add(TileSet.EMPTY_TILE);
        }
    }

    /**
     * Returns the tile id at a cell.
     *
     * @param x the tile x coordinate
     * @param y the tile y coordinate
     * @return the tile id
     */
    public int tile(int x, int y) {
        return tiles.get(index(x, y));
    }

    /**
     * Sets a tile id at a cell.
     *
     * @param x the tile x coordinate
     * @param y the tile y coordinate
     * @param tileId the tile id, or {@link TileSet#EMPTY_TILE}
     * @return this layer
     */
    public TileLayer tile(int x, int y, int tileId) {
        checkTileId(tileId);
        tiles.set(index(x, y), tileId);
        return this;
    }

    /**
     * Fills the layer with one tile id.
     *
     * @param tileId the tile id, or {@link TileSet#EMPTY_TILE}
     * @return this layer
     */
    public TileLayer fill(int tileId) {
        checkTileId(tileId);
        for (int i = 0; i < tiles.size(); i++) {
            tiles.set(i, tileId);
        }
        return this;
    }

    /**
     * Returns whether this layer is visible to renderers.
     *
     * @return true when visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this layer is visible to renderers.
     *
     * @param visible whether the layer is visible
     * @return this layer
     */
    public TileLayer visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Returns the width in tiles.
     *
     * @return the width in tiles
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height in tiles.
     *
     * @return the height in tiles
     */
    public int height() {
        return height;
    }

    /**
     * Returns the number of cells.
     *
     * @return the number of cells
     */
    public int size() {
        return tiles.size();
    }

    /**
     * Returns the tile ids as a copy.
     *
     * @return the tile ids
     */
    public int[] tiles() {
        return tiles.toArray();
    }

    private int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("x=" + x + ", y=" + y + ", width=" + width + ", height=" + height);
        }
        return y * width + x;
    }

    private void checkTileId(int tileId) {
        if (tileId < TileSet.EMPTY_TILE) {
            throw new FdxException("tileId must be >= 0");
        }
    }

    private int cellCount(int width, int height) {
        long size = (long)width * height;
        if (size > Integer.MAX_VALUE) {
            throw new FdxException("TileLayer cell count is too large");
        }
        return (int)size;
    }
}
