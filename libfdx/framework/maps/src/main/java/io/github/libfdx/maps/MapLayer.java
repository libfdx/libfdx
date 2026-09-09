package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Application-owned layer metadata. Coordinates are map units, positive Y upward; offsets are applied by renderers. */
public abstract class MapLayer {
    private int id;
    private String name = "";
    private String className = "";
    private MapProperties properties = new MapProperties();
    private boolean visible = true;
    private float opacity = 1;
    private float offsetX, offsetY;
    private float parallaxX = 1, parallaxY = 1;
    private int tintRgba = 0xffffffff;
    /** Packed RGBA color, multiplied through groups and with layer opacity; defaults to white. */
    public int tintRgba() { return tintRgba; }
    public MapLayer tint(int rgba) { tintRgba=rgba; return this; }
    public MapLayer metadata(int id, String name, String className, MapProperties properties) {
        if (id < 0 || name == null || className == null || properties == null) { throw new FdxException("Invalid layer metadata"); }
        this.id = id; this.name = name; this.className = className; this.properties = properties; return this;
    }
    public int id() { return id; }
    public String name() { return name; }
    public String className() { return className; }
    public MapProperties properties() { return properties; }
    public boolean isVisible() { return visible; }
    public MapLayer visible(boolean visible) { this.visible = visible; return this; }
    public float opacity() { return opacity; }
    public MapLayer opacity(float opacity) {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1) { throw new FdxException("Layer opacity must be in [0, 1]"); }
        this.opacity = opacity; return this;
    }
    /** Camera scrolling factors. Zero stays fixed in the view; negative factors reverse movement. */
    public MapLayer parallax(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) { throw new FdxException("Parallax factors must be finite"); }
        parallaxX = x; parallaxY = y; return this;
    }
    public float parallaxX() { return parallaxX; }
    public float parallaxY() { return parallaxY; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public MapLayer offset(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) { throw new FdxException("Layer offset must be finite"); }
        offsetX = x; offsetY = y; return this;
    }
}
