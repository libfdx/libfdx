package io.github.libfdx.graphics;

/**
 * Stable color-target write-mask bits.
 */
public final class ColorWriteMask {
    public static final int RED = 1;
    public static final int GREEN = 1 << 1;
    public static final int BLUE = 1 << 2;
    public static final int ALPHA = 1 << 3;
    public static final int ALL = RED | GREEN | BLUE | ALPHA;

    private ColorWriteMask() {
    }
}
