package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable CPU-prepared distant environment light. Contains diffuse irradiance divided by pi,
 * a GGX specular mip chain and a two-channel split-sum BRDF lookup, all in linear half float.
 * No GPU ownership. Creation/encoding allocates and belongs in tooling or asset preparation.
 *
 * <p>Environment images are 2:1 equirectangular, +Y at v=0, +X at u=.5, +Z at u=.75.
 * Specular level i represents perceptual roughness i/(levelCount-1). The chain may stop before 1x1
 * to preserve directional detail at high roughness; every allocated level must be supplied.
 * LUT X is NdotV and Y is perceptual roughness. Inputs are copied and never exposed mutable.</p>
 */
public final class ImageBasedLightingData {
    private static final int MAGIC = 0x49584446; // FDXI
    private static final int HEADER_BYTES = 32;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private final int specularWidth, diffuseWidth, lutSize;
    private final ByteBuffer[] specular;
    private final ByteBuffer diffuse, brdf;

    private ImageBasedLightingData(int specularWidth, int diffuseWidth, int lutSize,
            ByteBuffer[] specular, ByteBuffer diffuse, ByteBuffer brdf) {
        this.specularWidth = specularWidth;
        this.diffuseWidth = diffuseWidth;
        this.lutSize = lutSize;
        this.specular = specular;
        this.diffuse = diffuse;
        this.brdf = brdf;
    }

    /** Copies prepared RGB environment levels and RG BRDF coefficients, with round-to-even half conversion.
     * Widths must be powers of two (2..2048), LUT size 2..1024; total encoded data is bounded to 64 MiB.
     * RGB values must be finite in [0,65504], BRDF coefficients in [0,1]. No convolution is performed. */
    public static ImageBasedLightingData of(int specularWidth, float[][] specularRgb,
            int diffuseWidth, float[] diffuseRgb, int lutSize, float[] brdfRg) {
        int count = specularRgb == null ? 0 : specularRgb.length;
        byteSize(specularWidth, diffuseWidth, lutSize, count);
        ByteBuffer[] levels = new ByteBuffer[count];
        for (int i = 0; i < count; i++) {
            levels[i] = pixels(specularRgb[i], Math.max(1, specularWidth >> i)
                    * Math.max(1, (specularWidth / 2) >> i), 3);
        }
        return new ImageBasedLightingData(specularWidth, diffuseWidth, lutSize, levels,
                pixels(diffuseRgb, diffuseWidth * diffuseWidth / 2, 3), pixels(brdfRg, lutSize * lutSize, 2));
    }

    /** Decodes version 1 FDXI data, checking exact size, dimensions, encoding and finite pixel ranges.
     * File integers/half components and prepared upload buffers are little endian.
     * Invalid/trailing data fails before GPU creation. Input bytes are borrowed only for this call. */
    public static ImageBasedLightingData decode(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_BYTES || bytes.length > MAX_BYTES) fail("Invalid FDXI size");
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt() != MAGIC || input.getInt() != 1) fail("Unsupported FDXI magic/version");
        int width = input.getInt(), diffuseWidth = input.getInt(), lutSize = input.getInt();
        int count = input.getInt();
        int size = byteSize(width, diffuseWidth, lutSize, count);
        if (input.getInt() != 1 || input.getInt() != 0) fail("Unsupported FDXI layout");
        if (bytes.length != size) fail("FDXI byte count does not match dimensions");
        ByteBuffer[] levels = new ByteBuffer[count];
        for (int i = 0; i < count; i++) {
            levels[i] = readPixels(input, Math.max(1, width >> i) * Math.max(1, (width / 2) >> i), false);
        }
        return new ImageBasedLightingData(width, diffuseWidth, lutSize, levels,
                readPixels(input, diffuseWidth * diffuseWidth / 2, false), readPixels(input, lutSize * lutSize, true));
    }

    /** Returns a newly allocated, deterministic version 1 FDXI file. */
    public byte[] encode() {
        ByteBuffer output = ByteBuffer.allocate(encodedByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(MAGIC).putInt(1).putInt(specularWidth).putInt(diffuseWidth).putInt(lutSize)
                .putInt(specular.length).putInt(1).putInt(0);
        for (ByteBuffer level : specular) writePixels(output, level);
        writePixels(output, diffuse);
        writePixels(output, brdf);
        return output.array();
    }

    public int specularWidth() { return specularWidth; }
    public int specularHeight() { return specularWidth / 2; }
    public int specularLevelCount() { return specular.length; }
    public int diffuseWidth() { return diffuseWidth; }
    public int diffuseHeight() { return diffuseWidth / 2; }
    public int brdfSize() { return lutSize; }
    /** Encoded bytes, including the 32-byte file header. GPU texel bytes exclude that header. */
    public int encodedByteSize() { return byteSize(specularWidth, diffuseWidth, lutSize, specular.length); }

    ByteBuffer diffusePixels() { return view(diffuse); }
    ByteBuffer brdfPixels() { return view(brdf); }
    ByteBuffer[] specularPixels() {
        ByteBuffer[] views = new ByteBuffer[specular.length];
        for (int i = 0; i < views.length; i++) views[i] = view(specular[i]);
        return views;
    }
    private static ByteBuffer view(ByteBuffer pixels) { return pixels.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN); }

    private static int byteSize(int width, int diffuseWidth, int lutSize, int count) {
        dimension(width, 2048); dimension(diffuseWidth, 2048); dimension(lutSize, 1024);
        if (count < 2 || count > levelCount(width)) fail("Invalid specular mip count");
        long pixels = (long) diffuseWidth * diffuseWidth / 2 + (long) lutSize * lutSize;
        for (int i = 0; i < count; i++) pixels += (long) Math.max(1, width >> i) * Math.max(1, (width / 2) >> i);
        long size = HEADER_BYTES + pixels * 8;
        if (size > MAX_BYTES) fail("FDXI exceeds 64 MiB");
        return (int) size;
    }
    private static void dimension(int value, int max) {
        if (value < 2 || value > max || (value & (value - 1)) != 0) fail("FDXI dimensions must be bounded powers of two");
    }
    private static int levelCount(int width) { return 32 - Integer.numberOfLeadingZeros(width); }
    private static ByteBuffer pixels(float[] values, int count, int components) {
        if (values == null || values.length != count * components) fail("Prepared IBL pixel count mismatch");
        ByteBuffer pixels = ByteBuffer.allocateDirect(count * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < components; c++) {
                float value = values[i * components + c];
                if (!Float.isFinite(value) || value < 0 || value > (components == 2 ? 1 : 65504)) fail("Invalid IBL component");
                pixels.putShort(half(value));
            }
            if (components == 2) pixels.putShort((short) 0);
            pixels.putShort((short) 0x3c00);
        }
        return pixels.flip();
    }
    private static ByteBuffer readPixels(ByteBuffer input, int count, boolean lut) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(count * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count * 4; i++) {
            int half = input.getShort() & 0xffff;
            if (half >= 0x7c00 || ((i & 3) == 3 && half != 0x3c00)
                    || (lut && ((i & 3) < 2 ? half > 0x3c00 : (i & 3) == 2 && half != 0))) {
                fail("Invalid FDXI half-float component");
            }
            pixels.putShort((short) half);
        }
        return pixels.flip();
    }
    private static void writePixels(ByteBuffer output, ByteBuffer input) {
        for (int i = 0; i < input.limit(); i += 2) output.putShort(input.getShort(i));
    }
    // Input is already finite, nonnegative and at most the largest finite half.
    static short half(float value) {
        int bits = Float.floatToIntBits(value == 0 ? 0 : value);
        int exponent = (bits >>> 23) - 127;
        if (exponent < -25) return 0;
        if (exponent < -14) {
            int significand = (bits & 0x7fffff) | 0x800000;
            int shift = -exponent - 1;
            int result = significand >>> shift;
            int remainder = significand & ((1 << shift) - 1);
            int midpoint = 1 << (shift - 1);
            return (short) (result + (remainder > midpoint || (remainder == midpoint && (result & 1) != 0) ? 1 : 0));
        }
        return (short) ((bits + 0xfff + ((bits >>> 13) & 1) - 0x38000000) >>> 13);
    }
    static float fromHalf(int bits) {
        int exponent = bits >>> 10;
        return exponent == 0 ? (bits & 1023) * 0x1p-24f
                : Float.intBitsToFloat(((exponent + 112) << 23) | ((bits & 1023) << 13));
    }
    private static void fail(String message) { throw new FdxException(message); }
}
