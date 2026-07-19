package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;

/**
 * Owns the dimensions and layers for a 2D tile map.
 *
 * @author xpenatan
 */
public final class TileMap {
    private final int width;
    private final int height;
    private final float tileWidth;
    private final float tileHeight;
    private final Array<TileLayer> layers = new Array<TileLayer>();

    /**
     * Creates a tile map.
     *
     * @param width the width in tiles
     * @param height the height in tiles
     * @param tileWidth the tile width in render units
     * @param tileHeight the tile height in render units
     */
    public TileMap(int width, int height, float tileWidth, float tileHeight) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("TileMap size must be greater than zero");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new FdxException("TileMap tile size must be greater than zero");
        }
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /**
     * Creates and adds a layer with the map dimensions.
     *
     * @return the added layer
     */
    public TileLayer addLayer() {
        TileLayer layer = new TileLayer(width, height);
        layers.add(layer);
        return layer;
    }

    /**
     * Adds a layer.
     *
     * @param layer the layer
     * @return this map
     */
    public TileMap addLayer(TileLayer layer) {
        if (layer == null) {
            throw new FdxException("TileLayer cannot be null");
        }
        if (layer.width() != width || layer.height() != height) {
            throw new FdxException("TileLayer dimensions must match the TileMap dimensions");
        }
        layers.add(layer);
        return this;
    }

    /**
     * Returns a layer.
     *
     * @param index the layer index
     * @return the layer
     */
    public TileLayer layer(int index) {
        return layers.get(index);
    }

    /**
     * Removes a layer.
     *
     * @param index the layer index
     * @return the removed layer
     */
    public TileLayer removeLayer(int index) {
        return layers.removeIndex(index);
    }

    /**
     * Removes all layers.
     */
    public void clearLayers() {
        layers.clear();
    }

    /**
     * Returns the number of layers.
     *
     * @return the number of layers
     */
    public int layerCount() {
        return layers.size();
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
     * Returns the tile width in render units.
     *
     * @return the tile width
     */
    public float tileWidth() {
        return tileWidth;
    }

    /**
     * Returns the tile height in render units.
     *
     * @return the tile height
     */
    public float tileHeight() {
        return tileHeight;
    }

    /**
     * Returns the map width in render units.
     *
     * @return the map width
     */
    public float worldWidth() {
        return width * tileWidth;
    }

    /**
     * Returns the map height in render units.
     *
     * @return the map height
     */
    public float worldHeight() {
        return height * tileHeight;
    }
}
