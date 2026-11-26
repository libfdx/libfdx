package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.graphics.g2d.PixelArtViewport;

/** Shared integer viewport placement for the rendered world and UI Kit controls. */
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

}
