package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;

/**
 * Maps positive tile ids to texture regions.
 *
 * @author xpenatan
 */
public final class TileSet {
    /**
     * The empty tile id.
     */
    public static final int EMPTY_TILE = 0;

    private final IntMap<TextureRegion> regions = new IntMap<TextureRegion>();

    /**
     * Creates an empty tile set.
     */
    public TileSet() {
    }

    /**
     * Creates a tile set from split texture regions.
     *
     * @param regions the regions, indexed by row then column
     * @return the tile set
     */
    public static TileSet from(TextureRegion[][] regions) {
        if (regions == null) {
            throw new FdxException("Texture regions cannot be null");
        }
        TileSet tileSet = new TileSet();
        int tileId = 1;
        for (int row = 0; row < regions.length; row++) {
            TextureRegion[] rowRegions = regions[row];
            if (rowRegions == null) {
                throw new FdxException("Texture region row cannot be null");
            }
            for (int column = 0; column < rowRegions.length; column++) {
                tileSet.region(tileId++, rowRegions[column]);
            }
        }
        return tileSet;
    }

    /**
     * Adds or replaces a region.
     *
     * @param tileId the positive tile id
     * @param region the region
     * @return this tile set
     */
    public TileSet region(int tileId, TextureRegion region) {
        checkTileId(tileId);
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        regions.put(tileId, region);
        return this;
    }

    /**
     * Returns a region.
     *
     * @param tileId the tile id
     * @return the region, or null for empty or missing ids
     */
    public TextureRegion region(int tileId) {
        if (tileId == EMPTY_TILE) {
            return null;
        }
        checkTileId(tileId);
        return regions.get(tileId);
    }

    /**
     * Returns whether a region exists.
     *
     * @param tileId the tile id
     * @return true if a region exists
     */
    public boolean contains(int tileId) {
        if (tileId == EMPTY_TILE) {
            return false;
        }
        checkTileId(tileId);
        return regions.containsKey(tileId);
    }

    /**
     * Removes a region.
     *
     * @param tileId the tile id
     * @return the previous region, or null
     */
    public TextureRegion remove(int tileId) {
        if (tileId == EMPTY_TILE) {
            return null;
        }
        checkTileId(tileId);
        return regions.remove(tileId);
    }

    /**
     * Removes all regions.
     */
    public void clear() {
        regions.clear();
    }

    /**
     * Returns the number of mapped regions.
     *
     * @return the number of regions
     */
    public int size() {
        return regions.size();
    }

    private void checkTileId(int tileId) {
        if (tileId <= EMPTY_TILE) {
            throw new FdxException("tileId must be > 0");
        }
    }
}
