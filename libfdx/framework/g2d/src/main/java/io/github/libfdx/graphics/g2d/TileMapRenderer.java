package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;

/**
 * Draws tile maps through a caller-owned {@link Batch2D}.
 *
 * @author xpenatan
 */
public final class TileMapRenderer {
    /**
     * Creates a tile map renderer.
     */
    public TileMapRenderer() {
    }

    /**
     * Renders visible map layers.
     *
     * @param map the map
     * @param tileSet the tile set
     * @param batch the batch
     * @param x the world x coordinate
     * @param y the world y coordinate
     * @return the number of drawn tiles
     */
    public int render(TileMap map, TileSet tileSet, Batch2D batch, float x, float y) {
        validate(map, tileSet, batch);
        return renderRange(map, tileSet, batch, x, y, 0, map.width(), 0, map.height());
    }

    /**
     * Renders visible map layers inside a world-space visible rectangle.
     *
     * @param map the map
     * @param tileSet the tile set
     * @param batch the batch
     * @param x the map world x coordinate
     * @param y the map world y coordinate
     * @param visibleX the visible rectangle x coordinate
     * @param visibleY the visible rectangle y coordinate
     * @param visibleWidth the visible rectangle width
     * @param visibleHeight the visible rectangle height
     * @return the number of drawn tiles
     */
    public int render(TileMap map, TileSet tileSet, Batch2D batch, float x, float y,
            float visibleX, float visibleY, float visibleWidth, float visibleHeight) {
        validate(map, tileSet, batch);
        if (!isFinite(visibleX) || !isFinite(visibleY)) {
            throw new FdxException("Visible rectangle position must be finite");
        }
        if (visibleWidth <= 0.0f || visibleHeight <= 0.0f) {
            return 0;
        }
        if (!isFinite(visibleWidth) || !isFinite(visibleHeight)) {
            throw new FdxException("Visible rectangle size must be finite");
        }
        float tileWidth = map.tileWidth();
        float tileHeight = map.tileHeight();
        int startX = clamp((int)Math.floor((visibleX - x) / tileWidth), 0, map.width());
        int endX = clamp((int)Math.ceil((visibleX + visibleWidth - x) / tileWidth), 0, map.width());
        int startY = clamp((int)Math.floor((visibleY - y) / tileHeight), 0, map.height());
        int endY = clamp((int)Math.ceil((visibleY + visibleHeight - y) / tileHeight), 0, map.height());
        if (startX >= endX || startY >= endY) {
            return 0;
        }
        return renderRange(map, tileSet, batch, x, y, startX, endX, startY, endY);
    }

    private int renderRange(TileMap map, TileSet tileSet, Batch2D batch, float x, float y,
            int startX, int endX, int startY, int endY) {
        if (!isFinite(x) || !isFinite(y)) {
            throw new FdxException("TileMap render position must be finite");
        }
        int drawn = 0;
        float tileWidth = map.tileWidth();
        float tileHeight = map.tileHeight();
        for (int layerIndex = 0; layerIndex < map.layerCount(); layerIndex++) {
            TileLayer layer = map.layer(layerIndex);
            if (!layer.isVisible()) {
                continue;
            }
            for (int tileY = startY; tileY < endY; tileY++) {
                float drawY = y + tileY * tileHeight;
                for (int tileX = startX; tileX < endX; tileX++) {
                    int tileId = layer.tile(tileX, tileY);
                    if (tileId == TileSet.EMPTY_TILE) {
                        continue;
                    }
                    TextureRegion region = tileSet.region(tileId);
                    if (region == null) {
                        continue;
                    }
                    batch.draw(region, x + tileX * tileWidth, drawY, tileWidth, tileHeight);
                    drawn++;
                }
            }
        }
        return drawn;
    }

    private void validate(TileMap map, TileSet tileSet, Batch2D batch) {
        if (map == null) {
            throw new FdxException("TileMap cannot be null");
        }
        if (tileSet == null) {
            throw new FdxException("TileSet cannot be null");
        }
        if (batch == null) {
            throw new FdxException("Batch2D cannot be null");
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
