package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.loaders.AtlasData;

/** Immutable borrowed sprite view; valid only while its owning atlas and pages live. */
public final class AtlasRegion {
    private final SpriteAtlas atlas;
    private final AtlasData.Sprite data;
    private final TextureRegion region;
    AtlasRegion(SpriteAtlas atlas,AtlasData.Sprite data,TextureRegion region) {
        this.atlas=atlas; this.data=data; this.region=region;
    }
    public AtlasData.Sprite data() { atlas.check(); return data; }
    /** Cropped region; use draw to preserve the original-image pivot after trimming. */
    public TextureRegion region() { atlas.check(); return region; }
    /**
     * Draws with a world-space pivot, world units per original pixel, and rotation
     * in degrees. Positive Y points up. Negative scales mirror around the pivot.
     * No allocation, state mutation, or draw reordering; the caller owns batch state.
     */
    public void draw(Batch2D batch,float pivotWorldX,float pivotWorldY,float scaleX,float scaleY,float rotationDegrees) {
        atlas.check();
        float dx=(data.trimX()-data.pivotX()*data.originalWidth())*scaleX;
        float dy=(data.trimY()-data.pivotY()*data.originalHeight())*scaleY;
        batch.draw(region,pivotWorldX+dx,pivotWorldY+dy,data.width()*scaleX,data.height()*scaleY,-dx,-dy,rotationDegrees);
    }
}
