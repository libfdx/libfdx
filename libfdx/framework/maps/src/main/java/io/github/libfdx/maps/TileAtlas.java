package io.github.libfdx.maps;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;

/**
 * CPU-only tileset metadata, backed by a regular image atlas or a sparse image collection.
 * Pixel rectangles and paths are source data, never GPU resources. Collection storage is
 * proportional to its tile count, independent of gaps between local IDs.
 */
public final class TileAtlas {
    private final String name, imagePath;
    private final int imageWidth, imageHeight, tileWidth, tileHeight, columns, tileCount, margin, spacing;
    private final float offsetX, offsetY;
    private final MapProperties properties;
    private final TileImage[] images;
    private final IntMap<TileImage> imagesById;
    private final int localIdLimit;
    private final IntMap<MapProperties> tileProperties = new IntMap<MapProperties>();
    private final IntMap<String> tileClasses = new IntMap<String>();
    private final IntMap<TileAnimation> animations = new IntMap<TileAnimation>();
    private long animationFrames;
    public TileAtlas(String name, String imagePath, int imageWidth, int imageHeight, int tileWidth, int tileHeight,
            int columns, int tileCount, int margin, int spacing, float offsetX, float offsetY, MapProperties properties) {
        if (name == null || imagePath == null || imagePath.isEmpty() || properties == null
                || imageWidth <= 0 || imageHeight <= 0 || tileWidth <= 0 || tileHeight <= 0
                || columns <= 0 || tileCount <= 0 || margin < 0 || spacing < 0
                || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)
                || (long)margin * 2 + (long)columns * tileWidth + (long)(columns - 1) * spacing > imageWidth
                || (long)margin * 2 + ((tileCount - 1L) / columns + 1) * tileHeight
                        + ((tileCount - 1L) / columns) * spacing > imageHeight) {
            throw new FdxException("Invalid tile atlas dimensions: " + name);
        }
        this.name = name; this.imagePath = imagePath; this.imageWidth = imageWidth; this.imageHeight = imageHeight;
        this.tileWidth = tileWidth; this.tileHeight = tileHeight; this.columns = columns; this.tileCount = tileCount;
        this.margin = margin; this.spacing = spacing; this.offsetX = offsetX; this.offsetY = offsetY; this.properties = properties;
        images = null; imagesById = null; localIdLimit = tileCount;
    }
    /** Copies and sorts image metadata by local ID; duplicate IDs and oversize tiles fail. */
    public static TileAtlas imageCollection(String name, int tileWidth, int tileHeight, TileImage[] images,
            float offsetX, float offsetY, MapProperties properties) {
        return new TileAtlas(name, tileWidth, tileHeight, images, offsetX, offsetY, properties);
    }
    private TileAtlas(String name, int tileWidth, int tileHeight, TileImage[] images,
            float offsetX, float offsetY, MapProperties properties) {
        if (name == null || properties == null || images == null || images.length == 0
                || tileWidth <= 0 || tileHeight <= 0 || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new FdxException("Invalid image collection: " + name);
        }
        this.images = images.clone();
        imagesById = new IntMap<>();
        for (TileImage image : this.images) {
            if (image == null || image.width() > tileWidth || image.height() > tileHeight
                    || imagesById.containsKey(image.localId())) {
                throw new FdxException("Invalid or duplicate image collection tile: " + name);
            }
            imagesById.put(image.localId(), image);
        }
        java.util.Arrays.sort(this.images, (a, b) -> Integer.compare(a.localId(), b.localId()));
        this.name = name; this.tileWidth = tileWidth; this.tileHeight = tileHeight;
        this.tileCount = images.length; this.localIdLimit = this.images[images.length - 1].localId() + 1;
        this.offsetX = offsetX; this.offsetY = offsetY; this.properties = properties;
        imagePath = null; imageWidth = imageHeight = columns = margin = spacing = 0;
    }
    public String name() { return name; }
    /** Shared atlas image; fails for a collection. Use imagePath(localId) for either kind. */
    public String imagePath() { checkSharedImage(); return imagePath; }
    /** Shared atlas width; fails for a collection. */
    public int imageWidth() { checkSharedImage(); return imageWidth; }
    /** Shared atlas height; fails for a collection. */
    public int imageHeight() { checkSharedImage(); return imageHeight; }
    public boolean isImageCollection() { return images != null; }
    public int tileWidth() { return tileWidth; }
    public int tileHeight() { return tileHeight; }
    /** Atlas column count, or zero for an image collection. */
    public int columns() { return columns; }
    /** Number of existing tiles, not the size of a sparse ID range. */
    public int tileCount() { return tileCount; }
    /** Exclusive upper local ID bound; holes below this value need not exist. */
    public int localIdLimit() { return localIdLimit; }
    /** Local ID at an index in [0,tileCount), in ascending order; does not allocate. */
    public int localId(int index) {
        if (index < 0 || index >= tileCount) { throw new FdxException("Tile index out of range: " + index); }
        return images == null ? index : images[index].localId();
    }
    public boolean contains(int localId) {
        return localId >= 0 && localId < localIdLimit && (images == null || imagesById.containsKey(localId));
    }
    public int margin() { return margin; }
    public int spacing() { return spacing; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public MapProperties properties() { return properties; }
    public String imagePath(int localId) { check(localId); return images == null ? imagePath : imagesById.get(localId).imagePath(); }
    public int imageWidth(int localId) { check(localId); return images == null ? imageWidth : imagesById.get(localId).imageWidth(); }
    public int imageHeight(int localId) { check(localId); return images == null ? imageHeight : imagesById.get(localId).imageHeight(); }
    public int regionWidth(int localId) { check(localId); return images == null ? tileWidth : imagesById.get(localId).width(); }
    public int regionHeight(int localId) { check(localId); return images == null ? tileHeight : imagesById.get(localId).height(); }
    public int sourceX(int localId) { check(localId); return images == null ? margin + localId % columns * (tileWidth + spacing) : imagesById.get(localId).x(); }
    public int sourceY(int localId) { check(localId); return images == null ? margin + localId / columns * (tileHeight + spacing) : imagesById.get(localId).y(); }
    /** Returns tile-specific metadata, or null if none was authored. */
    public MapProperties tileProperties(int localId) { check(localId); return tileProperties.get(localId); }
    /** Returns the authored tile class, or the empty string. */
    public String tileClass(int localId) { check(localId); String value = tileClasses.get(localId); return value == null ? "" : value; }
    public TileAtlas tileMetadata(int localId, String className, MapProperties properties) {
        check(localId);
        if (className == null || properties == null) { throw new FdxException("Tile metadata cannot be null"); }
        tileProperties.put(localId, properties); tileClasses.put(localId, className); return this;
    }
    /** Returns immutable animation metadata, or null for a static tile. */
    public TileAnimation animation(int localId) { check(localId); return animations.get(localId); }
    /** Adds/replaces a sequence after checking every local image ID; null removes it. */
    public TileAtlas animation(int localId, TileAnimation animation) {
        check(localId);
        if (animation != null) {
            for (int i = 0; i < animation.frameCount(); i++) { check(animation.localId(i)); }
        }
        TileAnimation previous = animations.get(localId);
        if (previous != null) { animationFrames -= previous.frameCount(); }
        if (animation == null) { animations.remove(localId); }
        else { animations.put(localId, animation); animationFrames += animation.frameCount(); }
        return this;
    }
    /** Total frame entries across this atlas's animations. */
    public long animationFrameCount() { return animationFrames; }
    private void check(int localId) {
        if (!contains(localId)) { throw new FdxException("Missing local tile " + localId + " in " + name); }
    }
    private void checkSharedImage() {
        if (images != null) { throw new FdxException("Image collection " + name + " requires a local tile ID"); }
    }
}
