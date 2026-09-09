package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.maps.TileAnimation;

/**
 * Maps positive tile ids to borrowed texture regions. Bindings also cache a
 * texel-center sampling view for TileMapRenderer; public lookups/removal return
 * the original regions. Binding allocates, rendering reuses the cached views.
 *
 * @author xpenatan
 */
public final class TileSet {
    /**
     * The empty tile id.
     */
    public static final int EMPTY_TILE = 0;

    private final IntMap<Entry> regions = new IntMap<Entry>();
    private final ObjectMap<String,TextureRegion> images = new ObjectMap<>();
    /** Binds a borrowed image-layer region by canonical asset path; null removes it. */
    public TileSet imageRegion(String path,TextureRegion region) {
        if(path==null || path.isEmpty()) throw new FdxException("Image path required");
        if(region==null) images.remove(path); else images.put(path,region);
        return this;
    }
    /** Borrowed image region or null when unbound. Image layers are separate from tile IDs. */
    public TextureRegion findImage(String path) { return images.get(path); }
    private final IntMap<AnimationBinding> animations = new IntMap<>();
    private final Array<AnimationBinding> animationList = new Array<>(0);
    private long animationTime;
    float minX, minY, maxRight, maxTop;

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
        regions.put(tileId, new Entry(region, false, 0, 0));
        return this;
    }

    /** Binds an atlas tile at its native pixel size with a Y-up drawing offset. Textures are borrowed. */
    public TileSet atlasRegion(int tileId, TextureRegion region, float offsetX, float offsetY) {
        checkTileId(tileId);
        if (region == null || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new FdxException("Invalid atlas region");
        }
        regions.put(tileId, new Entry(region, true, offsetX, offsetY));
        // Conservative culling extents; removal/replacement may leave harmless padding.
        minX = Math.min(minX, offsetX); minY = Math.min(minY, offsetY);
        maxRight = Math.max(maxRight, offsetX + region.width());
        maxTop = Math.max(maxTop, offsetY + region.height());
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
        Entry entry = entry(tileId);
        return entry == null ? null : entry.region;
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
        animation(tileId, null, 1);
        Entry entry = regions.remove(tileId);
        return entry == null ? null : entry.region;
    }

    /**
     * Removes all regions.
     */
    public void clear() {
        regions.clear();
        images.clear();
        animations.clear(); animationList.clear(); animationTime = 0;
        minX = minY = maxRight = maxTop = 0;
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

    /**
     * Binds an immutable atlas-local animation to a global tile ID. Every frame image
     * must already be bound. Null removes the animation. Replacement images are picked
     * up on subsequent draws; a removed frame is skipped until rebound. Definitions
     * sample base images directly, including self-references, without recursion.
     */
    public TileSet animation(int tileId, TileAnimation animation, int firstId) {
        checkTileId(tileId);
        AnimationBinding binding = null;
        if (animation != null) {
            checkTileId(firstId);
            if (!regions.containsKey(tileId)) { throw new FdxException("Missing animated tile " + tileId); }
            for (int i = 0; i < animation.frameCount(); i++) {
                long id = (long)firstId + animation.localId(i);
                if (id > Integer.MAX_VALUE || !regions.containsKey((int)id)) {
                    throw new FdxException("Missing animation frame image " + id);
                }
            }
            binding = new AnimationBinding(animation, firstId);
            binding.imageId = firstId + animation.tileAt(animationTime);
        }
        AnimationBinding previous = animations.remove(tileId);
        if (previous != null) { animationList.removeValue(previous, true); }
        if (binding != null) { animations.put(tileId, binding); animationList.add(binding); }
        return this;
    }

    /**
     * Samples each animation once, with no allocation. Call once before drawing using
     * an application-owned clock; all cells and tile objects sharing this set share
     * its phase. Holding the clock pauses it. Clocks may seek or wrap backwards.
     */
    public TileSet animationTime(long elapsedMillis) {
        animationTime = elapsedMillis;
        for (int i = 0; i < animationList.size(); i++) {
            AnimationBinding binding = animationList.get(i);
            binding.imageId = binding.firstId + binding.animation.tileAt(elapsedMillis);
        }
        return this;
    }

    Entry entry(int tileId) {
        AnimationBinding binding = animations.get(tileId);
        return regions.get(binding == null ? tileId : binding.imageId);
    }
    private static final class AnimationBinding {
        final TileAnimation animation;
        final int firstId;
        int imageId;
        AnimationBinding(TileAnimation animation, int firstId) { this.animation = animation; this.firstId = firstId; }
    }
    static final class Entry {
        final TextureRegion region;
        final TextureRegion samplingRegion;
        final boolean nativeSize;
        final float offsetX, offsetY;
        Entry(TextureRegion region, boolean nativeSize, float offsetX, float offsetY) {
            this.region = region; this.samplingRegion = region.tileSamplingRegion(); this.nativeSize = nativeSize; this.offsetX = offsetX; this.offsetY = offsetY;
        }
    }
}
