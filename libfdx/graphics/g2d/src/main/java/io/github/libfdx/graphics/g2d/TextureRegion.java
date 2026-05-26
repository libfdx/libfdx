package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;

public final class TextureRegion {
    private final Texture texture;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float u;
    private final float v;
    private final float u2;
    private final float v2;

    public TextureRegion(Texture texture) {
        this(texture, 0, 0, texture != null ? texture.width() : 0, texture != null ? texture.height() : 0);
    }

    public TextureRegion(Texture texture, int x, int y, int width, int height) {
        if (texture == null) {
            throw new FdxException("TextureRegion texture cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new FdxException("TextureRegion size must be greater than zero");
        }
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.u = x / (float) texture.width();
        this.v = y / (float) texture.height();
        this.u2 = (x + width) / (float) texture.width();
        this.v2 = (y + height) / (float) texture.height();
    }

    public static TextureRegion[][] split(Texture texture, int tileWidth, int tileHeight) {
        if (texture == null) {
            throw new FdxException("Texture cannot be null");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new FdxException("Tile size must be greater than zero");
        }
        int columns = texture.width() / tileWidth;
        int rows = texture.height() / tileHeight;
        TextureRegion[][] regions = new TextureRegion[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                regions[row][column] = new TextureRegion(texture, column * tileWidth, row * tileHeight,
                        tileWidth, tileHeight);
            }
        }
        return regions;
    }

    public Texture texture() {
        return texture;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float u() {
        return u;
    }

    public float v() {
        return v;
    }

    public float u2() {
        return u2;
    }

    public float v2() {
        return v2;
    }
}
