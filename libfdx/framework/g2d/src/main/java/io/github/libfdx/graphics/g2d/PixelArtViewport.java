package io.github.libfdx.graphics.g2d;

import io.github.libfdx.graphics.RenderPass;

/**
 * Application-owned, allocation-free logical-pixel to clip-space mapping for Batch2D.
 * Fits a whole-number scale and centers the result. Extra framebuffer pixels become
 * letterbox margins. Windows smaller than the logical image crop a centered 1x image
 * rather than introducing fractional pixels. All screen arguments use framebuffer
 * pixels with a lower-left origin, not platform logical-window/input coordinates.
 */
public final class PixelArtViewport {
    private final int logicalWidth, logicalHeight;
    private int width, height, scale, x, y;
    private float cameraX, cameraY;
    private boolean snap = true;

    /** Creates a viewport with one logical unit per source pixel. */
    public PixelArtViewport(int logicalWidth, int logicalHeight) {
        if (logicalWidth < 1 || logicalHeight < 1) throw new IllegalArgumentException("Logical size must be positive");
        this.logicalWidth = logicalWidth; this.logicalHeight = logicalHeight;
        update(logicalWidth, logicalHeight);
    }
    /** Recomputes integer placement after framebuffer resize; rejects zero/minimized sizes. */
    public PixelArtViewport update(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Framebuffer size must be positive");
        this.width = width; this.height = height;
        scale = Math.max(1, Math.min(width / logicalWidth, height / logicalHeight));
        x = Math.floorDiv(width - logicalWidth * scale, 2);
        y = Math.floorDiv(height - logicalHeight * scale, 2);
        return this;
    }
    /** Sets the lower-left world camera position in logical pixels. Borrowed game state is never mutated. */
    public PixelArtViewport camera(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Camera must be finite");
        cameraX = x; cameraY = y; return this;
    }
    /** Enables logical-pixel camera and vertex snapping (default true); false allows subpixel movement. */
    public PixelArtViewport snap(boolean enabled) { snap = enabled; return this; }
    private float aligned(float value) { return snap ? (float) Math.floor(value + 0.5) : value; }
    /** Projects a world X to clip space for a batch drawing into the full framebuffer. */
    public float clipX(float worldX) { return (x + (aligned(worldX) - aligned(cameraX)) * scale) * 2f / width - 1; }
    /** Projects a world Y to clip space for a batch drawing into the full framebuffer. */
    public float clipY(float worldY) { return (y + (aligned(worldY) - aligned(cameraY)) * scale) * 2f / height - 1; }
    /** Converts a logical-pixel width to clip-space width; integral input preserves pixel alignment. */
    public float clipWidth(float pixels) { return pixels * scale * 2f / width; }
    /** Converts a logical-pixel height to clip-space height; integral input preserves pixel alignment. */
    public float clipHeight(float pixels) { return pixels * scale * 2f / height; }
    /** Maps framebuffer X back to the snapped camera's continuous world position. */
    public float worldX(float framebufferX) { return (framebufferX - x) / scale + aligned(cameraX); }
    /** Maps lower-left framebuffer Y back to world position. */
    public float worldY(float framebufferY) { return (framebufferY - y) / scale + aligned(cameraY); }
    /** True inside the visible logical image, excluding letterbox margins. */
    public boolean contains(float framebufferX, float framebufferY) {
        return framebufferX >= Math.max(0, x) && framebufferY >= Math.max(0, y)
                && framebufferX < Math.min(width, x + logicalWidth * scale)
                && framebufferY < Math.min(height, y + logicalHeight * scale);
    }
    /**
     * Sets full framebuffer viewport and clips to the logical image on a borrowed pass.
     * Apply before batch.begin(pass), and pass framebuffer size to batch.viewport().
     * The caller owns pass lifetime and restores scissor/viewport before other rendering.
     */
    public void apply(RenderPass pass) {
        if (pass == null) throw new IllegalArgumentException("Pass cannot be null");
        int left = Math.max(0, x), bottom = Math.max(0, y);
        pass.setViewport(0, 0, width, height);
        pass.setScissor(left, bottom, Math.min(width, x + logicalWidth * scale) - left,
                Math.min(height, y + logicalHeight * scale) - bottom);
    }
    /** Integer output scale; always at least one. */
    public int scale() { return scale; }
    /** Lower-left image placement, possibly negative when cropped. */
    public int x() { return x; }
    /** Lower-left image placement, possibly negative when cropped. */
    public int y() { return y; }
    /** Logical image width. */
    public int logicalWidth() { return logicalWidth; }
    /** Logical image height. */
    public int logicalHeight() { return logicalHeight; }
}
