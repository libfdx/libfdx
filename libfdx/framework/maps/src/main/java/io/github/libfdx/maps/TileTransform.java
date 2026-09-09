package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Compact cell transforms in image coordinates: diagonal axis swap, then horizontal/vertical flips. */
public final class TileTransform {
    public static final int EMPTY_TILE = 0;
    public static final int FLIP_X = 1;
    public static final int FLIP_Y = 2;
    public static final int DIAGONAL = 4;
    private TileTransform() { }
    public static void check(int transform) {
        if ((transform & ~7) != 0) { throw new FdxException("Unknown tile transform: " + transform); }
    }
    /** Destination corner for a source corner: bottom-left, top-left, top-right, bottom-right. */
    public static int corner(int source, int transform) {
        if (source < 0 || source > 3) { throw new FdxException("Corner must be in [0, 3]"); }
        check(transform);
        int x = source >= 2 ? 1 : 0;
        int y = source == 0 || source == 3 ? 1 : 0;
        if ((transform & DIAGONAL) != 0) { int swap = x; x = y; y = swap; }
        if ((transform & FLIP_X) != 0) { x = 1 - x; }
        if ((transform & FLIP_Y) != 0) { y = 1 - y; }
        return x == 0 ? (y == 0 ? 1 : 0) : (y == 0 ? 2 : 3);
    }
}
