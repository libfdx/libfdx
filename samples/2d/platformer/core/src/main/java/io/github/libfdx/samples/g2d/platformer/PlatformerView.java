package io.github.libfdx.samples.g2d.platformer;

/** Continuous camera projection in authored map units, independent of UI and texture resolution. */
public final class PlatformerView {
    private int windowWidth, windowHeight, frameWidth, frameHeight;
    private float worldWidth, worldHeight;

    public void update(int width, int height, int framebufferWidth, int framebufferHeight) {
        if (width <= 0 || height <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) return;
        windowWidth = width;
        windowHeight = height;
        frameWidth = framebufferWidth;
        frameHeight = framebufferHeight;
        float aspect = frameWidth / (float) frameHeight;
        worldWidth = Math.max(300, Math.min(850, 264 * aspect));
        worldHeight = worldWidth / aspect;
    }

    public float width() { return worldWidth; }

    public float height() { return worldHeight; }

    public int frameWidth() { return frameWidth; }

    public int frameHeight() { return frameHeight; }

    public float uiScale() {
        float fit = Math.min(windowWidth / 360f, windowHeight / 320f);
        float designScale = Math.min(1.35f, Math.max(1, Math.min(windowWidth / 1440f, windowHeight / 850f)));
        return Math.min(4, Math.max(.25f, Math.min(fit, designScale) * frameWidth / Math.max(1f, windowWidth)));
    }

}
