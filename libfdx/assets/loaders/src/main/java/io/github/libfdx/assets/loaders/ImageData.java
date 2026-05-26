package io.github.libfdx.assets.loaders;

import java.nio.ByteBuffer;

public final class ImageData {
    private final int width;
    private final int height;
    private final ByteBuffer rgba;

    public ImageData(int width, int height, ByteBuffer rgba) {
        this.width = width;
        this.height = height;
        this.rgba = rgba != null ? rgba.slice() : ByteBuffer.allocateDirect(0);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public ByteBuffer rgba() {
        ByteBuffer copy = rgba.duplicate();
        copy.clear();
        return copy;
    }
}
