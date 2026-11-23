package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/**
 * Shared geometry for one tile definition. Shape order and IDs come from its collision group.
 * Shape anchors and rotated points are in Y-up pixels relative to the tile image's bottom left.
 * Placement writes interleaved X/Y coordinates into caller-owned storage without allocating.
 * Points for ellipses and capsules describe their bounding frames. Use the shape kind and its
 * original dimensions to interpret these frames after scaling or reflection.
 */
public final class TileCollision {
    private final MapObject[] shapes;
    private final int width, height;
    private final float offsetX, offsetY;
    private final long pointCount;

    TileCollision(MapObject[] shapes, int width, int height, float offsetX, float offsetY) {
        this.shapes = shapes.clone();
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        long total = 0;
        for (MapObject shape : this.shapes) {
            if (shape == null || shape.shape() == MapObject.Shape.TILE) {
                throw new FdxException("Expected collision geometry");
            }
            total += shape.pointCount();
        }
        pointCount = total;
    }

    public int shapeCount() {
        return shapes.length;
    }

    /** Returns borrowed, tile-local metadata for the shape at the given index in editor order. */
    public MapObject shape(int index) {
        return shapes[index];
    }

    long pointCount() {
        return pointCount;
    }

    /**
     * Places one shape at the cell's bottom-left world position, including any layer/group
     * offsets supplied by the caller. Applies the tileset offset and the cell's transform.
     * The output must have room for two floats per shape point starting at the given offset.
     */
    public void writeCellPoints(int shapeIndex, float cellX, float cellY, int transform, float[] out, int offset) {
        TileTransform.check(transform);
        writePoints(shapes[shapeIndex], transform, cellX, cellY, null, out, offset);
    }

    /**
     * Places one shape on a tile object using its authored size, anchor, rotation and flips.
     * Supply the accumulated world-space layer/group offsets and the collision definition for
     * the object's tile ID. The output layout is the same as for {@link #writeCellPoints}.
     */
    public void writeObjectPoints(int shapeIndex, MapObject tileObject, float layerX, float layerY, float[] out, int offset) {
        if (tileObject == null || tileObject.shape() != MapObject.Shape.TILE) {
            throw new FdxException("Expected a tile object for collision placement");
        }
        writePoints(shapes[shapeIndex], tileObject.transform(), layerX, layerY, tileObject, out, offset);
    }

    private void writePoints(MapObject shape, int transform, float originX, float originY,
            MapObject placement, float[] out, int offset) {
        int count = shape.pointCount();
        if (out == null || offset < 0 || offset > out.length - 2 * count) {
            throw new FdxException("Insufficient output space for " + count + " collision points");
        }
        double scaleX = placement == null ? 1 : (double) placement.width() / width;
        double scaleY = placement == null ? 1 : (double) placement.height() / height;
        for (int i = 0; i < count; i++) {
            double x = shape.worldX(i);
            double y = shape.worldY(i);
            if ((transform & TileTransform.DIAGONAL) != 0) {
                // Swap axes in normalized top-left image coordinates, preserving the destination rectangle.
                double oldX = x;
                x = (height - y) * width / height;
                y = (width - oldX) * height / width;
            }
            if ((transform & TileTransform.FLIP_X) != 0) {
                x = width - x;
            }
            if ((transform & TileTransform.FLIP_Y) != 0) {
                y = height - y;
            }
            float localX = (float) ((x + offsetX) * scaleX);
            float localY = (float) ((y + offsetY) * scaleY);
            out[offset++] = originX + (placement == null ? localX : placement.worldX(localX, localY));
            out[offset++] = originY + (placement == null ? localY : placement.worldY(localX, localY));
        }
    }

}
