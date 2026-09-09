package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/**
 * CPU image-layer data. The anchor is the image's top-left in map-local Y-up
 * coordinates, before inherited layer offsets/parallax. Image dimensions may
 * both be zero when the source format omits them; the graphics binding resolves
 * actual dimensions. Repetition uses native image pixels, with no stretching.
 */
public final class ImageLayer extends MapLayer {
    private final String imagePath;
    private final int imageWidth,imageHeight;
    private final float x,y;
    private final boolean repeatX,repeatY;
    public ImageLayer(String imagePath,int imageWidth,int imageHeight,float x,float y,boolean repeatX,boolean repeatY) {
        if(imagePath==null || imagePath.isEmpty() || imageWidth<0 || imageHeight<0
                || (imageWidth==0)!=(imageHeight==0) || !Float.isFinite(x) || !Float.isFinite(y)) {
            throw new FdxException("Invalid image layer source, dimensions, or anchor");
        }
        this.imagePath=imagePath; this.imageWidth=imageWidth; this.imageHeight=imageHeight;
        this.x=x; this.y=y; this.repeatX=repeatX; this.repeatY=repeatY;
    }
    public String imagePath() { return imagePath; }
    public int imageWidth() { return imageWidth; }
    public int imageHeight() { return imageHeight; }
    public float x() { return x; }
    public float y() { return y; }
    public boolean repeatX() { return repeatX; }
    public boolean repeatY() { return repeatY; }
}
