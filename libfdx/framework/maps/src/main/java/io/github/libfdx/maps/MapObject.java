package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/**
 * Immutable shape metadata in Y-up map units. Position is the author's anchor,
 * rotation is counterclockwise, and vertices are relative to the anchor.
 * Rectangle vertices extend downward from a top-left anchor; tile vertices extend
 * upward from a bottom-left anchor. Layer offsets are separate.
 */
public final class MapObject {
    public enum Shape { POINT, RECTANGLE, POLYGON, POLYLINE, TILE }
    private final int id, tileId, transform;
    private final String name, className;
    private final Shape shape;
    private final float x, y, width, height, rotation, opacity, cos, sin;
    private final boolean visible;
    private final float[] vertices;
    private final MapProperties properties;
    public MapObject(int id, String name, String className, Shape shape, float x, float y,
            float width, float height, float rotation, boolean visible, float opacity,
            int tileId, int transform, float[] vertices, MapProperties properties) {
        if (id <= 0 || name == null || className == null || shape == null || properties == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || width < 0
                || !Float.isFinite(height) || height < 0 || !Float.isFinite(rotation)
                || !Float.isFinite(opacity) || opacity < 0 || opacity > 1 || tileId < 0) {
            throw new FdxException("Invalid map object " + id);
        }
        TileTransform.check(transform);
        if (vertices == null || (vertices.length & 1) != 0) { throw new FdxException("Invalid object vertices"); }
        boolean validShape = switch (shape) {
            case POINT -> vertices.length == 2;
            case RECTANGLE -> vertices.length == 8;
            case TILE -> vertices.length == 8 && tileId > 0 && width > 0 && height > 0;
            case POLYGON -> vertices.length >= 6;
            case POLYLINE -> vertices.length >= 4;
        };
        if (!validShape || shape != Shape.TILE && (tileId != 0 || transform != 0)) {
            throw new FdxException("Invalid geometry for object " + id + " (" + shape + ")");
        }
        for (float coordinate : vertices) {
            if (!Float.isFinite(coordinate)) { throw new FdxException("Object vertices must be finite"); }
        }
        this.id = id; this.name = name; this.className = className; this.shape = shape;
        this.x = x; this.y = y; this.width = width; this.height = height; this.rotation = rotation;
        this.visible = visible; this.opacity = opacity; this.tileId = tileId; this.transform = transform;
        this.vertices = vertices.clone(); this.properties = properties;
        cos = (float)Math.cos(Math.toRadians(rotation)); sin = (float)Math.sin(Math.toRadians(rotation));
    }
    public int id() { return id; }
    public String name() { return name; }
    public String className() { return className; }
    public Shape shape() { return shape; }
    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    public float rotationDegrees() { return rotation; }
    public boolean isVisible() { return visible; }
    public float opacity() { return opacity; }
    public int tileId() { return tileId; }
    public int transform() { return transform; }
    public MapProperties properties() { return properties; }
    public int pointCount() { return vertices.length / 2; }
    public float pointX(int index) { return vertices[index * 2]; }
    public float pointY(int index) { return vertices[index * 2 + 1]; }
    public float worldX(int index) { return x + pointX(index) * cos - pointY(index) * sin; }
    public float worldY(int index) { return y + pointX(index) * sin + pointY(index) * cos; }
}
