package io.github.libfdx.maps;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;

/**
 * Owns the dimensions and layers for a 2D tile map.
 *
 * @author xpenatan
 */
public class TileMap {
    private final int width;
    private final int height;
    private final float tileWidth;
    private final float tileHeight;
    private final boolean infinite;
    private final Array<TileLayer> layers = new Array<TileLayer>();
    private final Array<MapLayer> mapLayers = new Array<MapLayer>();
    private final Array<TileAtlas> atlases = new Array<TileAtlas>(0);
    private final IntArray firstIds = new IntArray(0);
    private MapProperties properties = new MapProperties();
    private String className = "";
    private boolean reverseX, reverseY;
    private float parallaxOriginX, parallaxOriginY;

    /**
     * Creates a tile map.
     *
     * @param width the width in tiles
     * @param height the height in tiles
     * @param tileWidth the tile width in render units
     * @param tileHeight the tile height in render units
     */
    public TileMap(int width, int height, float tileWidth, float tileHeight) {
        this(width, height, tileWidth, tileHeight, false);
    }

    /** Creates an unbounded sparse map. width/height/worldWidth/worldHeight return zero, not a resident extent. */
    public static TileMap infinite(float tileWidth, float tileHeight) {
        return new TileMap(0, 0, tileWidth, tileHeight, true);
    }

    private TileMap(int width, int height, float tileWidth, float tileHeight, boolean infinite) {
        if (!infinite && (width <= 0 || height <= 0)) {
            throw new FdxException("TileMap size must be greater than zero");
        }
        if (!Float.isFinite(tileWidth) || !Float.isFinite(tileHeight) || tileWidth <= 0 || tileHeight <= 0
                || !Float.isFinite(width * tileWidth) || !Float.isFinite(height * tileHeight)) {
            throw new FdxException("TileMap tile size must be greater than zero");
        }
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.infinite = infinite;
    }

    public boolean isInfinite() { return infinite; }

    /**
     * Creates and adds a layer with the map dimensions.
     *
     * @return the added layer
     */
    public TileLayer addLayer() {
        if (infinite) throw new FdxException("Infinite maps require explicit chunked layers");
        TileLayer layer = new TileLayer(width, height);
        addLayer(layer);
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
        if (infinite || layer.width() != width || layer.height() != height) {
            throw new FdxException("TileLayer dimensions must match the TileMap dimensions");
        }
        layers.add(layer);
        mapLayers.add(layer);
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
        TileLayer layer = layers.removeIndex(index);
        mapLayers.removeValue(layer, true);
        return layer;
    }

    /**
     * Removes all layers.
     */
    public void clearLayers() {
        layers.clear();
        mapLayers.clear();
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

    /** Top-level layers in authoring order. Legacy layer(index) enumerates top-level tile layers only. */
    public MapLayer mapLayer(int index) { return mapLayers.get(index); }
    public int mapLayerCount() { return mapLayers.size(); }
    /** Adds a borrowed sparse layer to an infinite map in authoring order. Finite layer(index) does not enumerate these. */
    public TileMap addChunkedLayer(ChunkedTileLayer layer) {
        if (layer == null || !infinite) throw new FdxException("Chunked layers require an infinite map");
        mapLayers.add(layer); return this;
    }
    public TileMap addImageLayer(ImageLayer layer) {
        if(layer==null) throw new FdxException("ImageLayer cannot be null");
        mapLayers.add(layer); return this;
    }
    public TileMap addObjectLayer(ObjectLayer layer) {
        if (layer == null) { throw new FdxException("ObjectLayer cannot be null"); }
        mapLayers.add(layer); return this;
    }
    public TileMap addGroupLayer(GroupLayer layer) {
        if (layer == null) { throw new FdxException("GroupLayer cannot be null"); }
        validateGroup(layer);
        mapLayers.add(layer); return this;
    }
    private void validateGroup(GroupLayer group) {
        for (int i = 0; i < group.layerCount(); i++) {
            MapLayer child = group.layer(i);
            if (child instanceof TileLayer tiles && (infinite || tiles.width() != width || tiles.height() != height)) {
                throw new FdxException("Grouped tile dimensions must match the map");
            }
            if (child instanceof ChunkedTileLayer && !infinite) throw new FdxException("Chunked layers require an infinite map");
            if (child instanceof GroupLayer nested) { validateGroup(nested); }
        }
    }
    /** Parallax reference point in map-local, Y-up units; defaults to (0, 0). */
    public TileMap parallaxOrigin(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) { throw new FdxException("Parallax origin must be finite"); }
        parallaxOriginX = x; parallaxOriginY = y; return this;
    }
    public float parallaxOriginX() { return parallaxOriginX; }
    public float parallaxOriginY() { return parallaxOriginY; }
    public MapProperties properties() { return properties; }
    public String className() { return className; }
    public TileMap metadata(String className, MapProperties properties) {
        if (className == null || properties == null) { throw new FdxException("Map metadata cannot be null"); }
        this.className = className; this.properties = properties; return this;
    }
    /** Cell traversal direction; independent of storage's bottom-left origin. */
    public TileMap renderOrder(boolean reverseX, boolean reverseY) {
        this.reverseX = reverseX; this.reverseY = reverseY; return this;
    }
    public boolean reverseX() { return reverseX; }
    public boolean reverseY() { return reverseY; }
    public TileMap addAtlas(int firstId, TileAtlas atlas) {
        if (atlas == null || firstId <= 0 || (long)firstId + atlas.localIdLimit() - 1 > Integer.MAX_VALUE
                || !atlases.isEmpty() && firstId < (long)firstIds.get(firstIds.size() - 1) + atlases.get(atlases.size() - 1).localIdLimit()) {
            throw new FdxException("Invalid or overlapping atlas range: " + firstId);
        }
        firstIds.add(firstId); atlases.add(atlas); return this;
    }
    public int atlasCount() { return atlases.size(); }
    public TileAtlas atlas(int index) { return atlases.get(index); }
    public int firstId(int index) { return firstIds.get(index); }
    /** Returns the atlas index for an existing ID, or -1 for empty/unmapped IDs (including collection holes). */
    public int findAtlas(int tileId) {
        if (tileId <= 0) { return -1; }
        int low = 0, high = firstIds.size() - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (firstIds.get(middle) <= tileId) { low = middle + 1; }
            else { high = middle - 1; }
        }
        return high >= 0 && atlases.get(high).contains(tileId - firstIds.get(high)) ? high : -1;
    }
    /** Returns an object by stable ID, or null when absent. */
    public MapObject findObject(int id) {
        for (int i = 0; i < mapLayers.size(); i++) {
            MapObject found = findObject(mapLayers.get(i), id);
            if (found != null) { return found; }
        }
        return null;
    }
    private static MapObject findObject(MapLayer layer, int id) {
        if (layer instanceof ObjectLayer objects) {
            for (int i = 0; i < objects.objectCount(); i++) {
                if (objects.object(i).id() == id) { return objects.object(i); }
            }
        } else if (layer instanceof GroupLayer group) {
            for (int i = 0; i < group.layerCount(); i++) {
                MapObject found = findObject(group.layer(i), id);
                if (found != null) { return found; }
            }
        }
        return null;
    }
}
