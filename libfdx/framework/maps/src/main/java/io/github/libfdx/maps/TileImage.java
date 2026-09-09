package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Immutable CPU image/crop metadata for one sparse local tile ID. Crop coordinates are image top-left. */
public record TileImage(int localId, String imagePath, int imageWidth, int imageHeight,
                        int x, int y, int width, int height) {
    public TileImage {
        if (localId < 0 || localId == Integer.MAX_VALUE || imagePath == null || imagePath.isEmpty()
                || imageWidth <= 0 || imageHeight <= 0 || x < 0 || y < 0 || width <= 0 || height <= 0
                || (long)x + width > imageWidth || (long)y + height > imageHeight) {
            throw new FdxException("Invalid image for local tile " + localId + ": " + imagePath);
        }
    }
    /** Uses the complete image as the tile. Paths are metadata; no file or graphics resource is opened. */
    public TileImage(int localId, String imagePath, int width, int height) {
        this(localId, imagePath, width, height, 0, 0, width, height);
    }
}
