package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.graphics.g2d.PixelArtViewport;
import io.github.libfdx.input.InputBinding;

/** Shared integer viewport placement for rendering and window-coordinate UI/touch hit testing. */
public final class PlatformerView {
    public static final int WIDTH = 300, HEIGHT = 180;
    private final PixelArtViewport viewport = new PixelArtViewport(WIDTH, HEIGHT);
    private int windowWidth = WIDTH, windowHeight = HEIGHT, frameWidth = WIDTH, frameHeight = HEIGHT, revision;
    public void update(int windowWidth, int windowHeight, int frameWidth, int frameHeight) {
        if (windowWidth < 1 || windowHeight < 1 || frameWidth < 1 || frameHeight < 1) { return; }
        if (this.windowWidth != windowWidth || this.windowHeight != windowHeight
                || this.frameWidth != frameWidth || this.frameHeight != frameHeight) { revision++; }
        this.windowWidth = windowWidth; this.windowHeight = windowHeight;
        this.frameWidth = frameWidth; this.frameHeight = frameHeight;
        viewport.update(frameWidth, frameHeight);
    }
    public int revision() { return revision; }
    public PixelArtViewport viewport() { return viewport; }
    public float x(float windowX) { return (windowX*frameWidth/windowWidth-viewport.x())/viewport.scale(); }
    public float y(float windowY) { return (frameHeight-windowY*frameHeight/windowHeight-viewport.y())/viewport.scale(); }
    public boolean contains(float windowX, float windowY) {
        return viewport.contains(windowX*frameWidth/windowWidth, frameHeight-windowY*frameHeight/windowHeight);
    }
    /** Returns a clipped normalized touch binding, or null when its button is fully cropped away. */
    public InputBinding touch(float x, float y, float width, float height, float scale) {
        float left = Math.max(0, (viewport.x()+x*viewport.scale())/frameWidth);
        float top = Math.max(0, (frameHeight-viewport.y()-(y+height)*viewport.scale())/frameHeight);
        float right = Math.min(1, (viewport.x()+(x+width)*viewport.scale())/frameWidth);
        float bottom = Math.min(1, (frameHeight-viewport.y()-y*viewport.scale())/frameHeight);
        return right <= left || bottom <= top ? null : InputBinding.touch(left, top, right-left, bottom-top, scale);
    }
    public static boolean hit(float px, float py, float x, float y, float width, float height) {
        return px >= x && px < x+width && py >= y && py < y+height;
    }
}
