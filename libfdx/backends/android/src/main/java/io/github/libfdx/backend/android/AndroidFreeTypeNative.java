package io.github.libfdx.backend.android;

import java.nio.ByteBuffer;

/**
 * Provides native bindings for android free type.
 *
 * @author xpenatan
 */
final class AndroidFreeTypeNative {
    static {
        System.loadLibrary("fdx");
    }

    private AndroidFreeTypeNative() {
    }

    static native int rasterize(byte[] fontData, int fontDataSize, int[] codePoints, int codePointCount,
            float pixelSize, int padding, int atlasWidth, int[] metricInts, float[] metricFloats,
            ByteBuffer rgba, int rgbaSize, int[] glyphInts, int glyphIntCount, float[] glyphFloats,
            int glyphFloatCount, int[] kerningInts, int kerningIntCount);
}
