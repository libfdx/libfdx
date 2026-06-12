package io.github.libfdx.assets.loaders;

import java.nio.ByteBuffer;

/**
 * Represents an image data.
 *
 * @author xpenatan
 */
public final class ImageData {
    private final int width;
    private final int height;
    private final ByteBuffer rgba;

    /**
     * Creates an image data.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param rgba the RGBA
     */
    public ImageData(int width, int height, ByteBuffer rgba) {
        this.width = width;
        this.height = height;
        this.rgba = rgba != null ? rgba.slice() : ByteBuffer.allocateDirect(0);
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Returns the RGBA.
     *
     * @return the RGBA
     */
    public ByteBuffer rgba() {
        ByteBuffer copy = rgba.duplicate();
        copy.clear();
        return copy;
    }
}
