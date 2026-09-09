package io.github.libfdx.maps;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;

/**
 * Stores tile ids for one 2D map layer.
 *
 * @author xpenatan
 */
public class TileLayer extends MapLayer {
    private final int width;
    private final int height;
    private final IntArray tiles;
    private final byte[] transforms;
    private long revision;

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
        this.transforms = new byte[size];
        for (int i = 0; i < size; i++) {
            tiles.add(TileTransform.EMPTY_TILE);
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
     * @param tileId the tile id, or {@link TileTransform#EMPTY_TILE}
     * @return this layer
     */
    public TileLayer tile(int x, int y, int tileId) {
        return tile(x, y, tileId, 0);
    }

    /** Sets a positive ID and compact transform; empty cells always have no transform. */
    public TileLayer tile(int x, int y, int tileId, int transform) {
        checkTileId(tileId);
        TileTransform.check(transform);
        int index = index(x, y);
        byte flags = (byte)(tileId == 0 ? 0 : transform);
        if (tiles.get(index) != tileId || transforms[index] != flags) {
            tiles.set(index, tileId);
            transforms[index] = flags;
            revision++;
        }
        return this;
    }

    public int transform(int x, int y) { return transforms[index(x, y)]; }

    /** Cell-content revision. Changes only when IDs/transforms change, independent of layer metadata. */
    public long revision() { return revision; }

    /**
     * Fills the layer with one tile id.
     *
     * @param tileId the tile id, or {@link TileTransform#EMPTY_TILE}
     * @return this layer
     */
    public TileLayer fill(int tileId) {
        checkTileId(tileId);
        boolean changed = false;
        for (int i = 0; i < tiles.size(); i++) {
            changed |= tiles.get(i) != tileId || transforms[i] != 0;
            tiles.set(i, tileId);
            transforms[i] = 0;
        }
        if (changed) revision++;
        return this;
    }

    /**
     * Returns whether this layer is visible to renderers.
     *
     * @return true when visible
     */
    public boolean isVisible() {
        return super.isVisible();
    }

    /**
     * Sets whether this layer is visible to renderers.
     *
     * @param visible whether the layer is visible
     * @return this layer
     */
    public TileLayer visible(boolean visible) {
        super.visible(visible);
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
        if (tileId < TileTransform.EMPTY_TILE) {
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
