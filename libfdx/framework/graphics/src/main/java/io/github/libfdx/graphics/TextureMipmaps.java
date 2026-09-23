package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/** CPU asset-preparation helpers. These allocate copied level data and do not belong in a frame loop. */
public final class TextureMipmaps {
    private TextureMipmaps() { }

    /** Full chain length for positive dimensions, using floor-halving down to 1x1. */
    public static int levelCount(int width, int height) {
        if (width <= 0 || height <= 0) throw new FdxException("Mip dimensions must be positive");
        return 32-Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    /** Copies tightly packed RGBA8 from the current position and creates the complete chain.
     * Every returned direct buffer is owned by the caller, positioned at zero. Input position/limit
     * and bytes remain unchanged. Area weights include every texel at odd dimensions.
     * With srgb=true, RGB is averaged in linear space and re-encoded; alpha stays linear.
     * With alphaWeighted=true, straight RGB is weighted by alpha and stored straight after filtering,
     * preventing transparent RGB from bleeding into visible mips. Use false for data maps or opaque
     * material slots whose alpha is ignored. This does not preserve alpha-test coverage or normalize
     * normal vectors; those need content-specific preparation. */
    public static ByteBuffer[] rgba8(ByteBuffer source, int width, int height, boolean srgb, boolean alphaWeighted) {
        Rgba8Preparation preparation = prepareRgba8(source, width, height, srgb, alphaWeighted);
        while (!preparation.step(4096)) { }
        return preparation.result();
    }

    /** Creates CPU-only work that can yield between pixel batches. Borrows source until complete. */
    public static Rgba8Preparation prepareRgba8(ByteBuffer source, int width, int height,
            boolean srgb, boolean alphaWeighted) {
        return new Rgba8Preparation(source, width, height, srgb, alphaWeighted);
    }

    /** One caller at a time, on a worker or application thread. No graphics resources are touched. */
    public static final class Rgba8Preparation {
        private final ByteBuffer source;
        private final ByteBuffer[] levels;
        private final boolean srgb, alphaWeighted;
        private int width, height, level, pixel;

        private Rgba8Preparation(ByteBuffer source, int width, int height, boolean srgb, boolean alphaWeighted) {
            int count = levelCount(width, height);
            long bytes = (long)width*height*4;
            if (bytes > Integer.MAX_VALUE || source == null || source.remaining() < bytes) {
                throw new FdxException("RGBA8 source range is too small or exceeds buffer size limit");
            }
            this.width = width; this.height = height; this.srgb = srgb; this.alphaWeighted = alphaWeighted;
            this.source = source.slice();
            levels = new ByteBuffer[count];
            levels[0] = ByteBuffer.allocateDirect((int)bytes);
        }

        /** Processes at most maxPixels output pixels, including the base-level copy. */
        public boolean step(int maxPixels) {
            if (maxPixels < 1) throw new FdxException("Mip pixel budget must be positive");
            if (level == levels.length) return true;
            if (level == 0) {
                int end = (int)Math.min((long)width * height, (long)pixel + maxPixels);
                source.limit(end*4).position(pixel*4);
                levels[0].put(source);
                pixel = end;
                if (pixel == width*height) { levels[0].flip(); pixel = 0; level++; }
                return level == levels.length;
            }
            int nextWidth = Math.max(1, width/2), nextHeight = Math.max(1, height/2);
            if (levels[level] == null) levels[level] = ByteBuffer.allocateDirect(nextWidth*nextHeight*4);
            // Filtering is substantially dearer than a bulk base-level copy.
            int end = (int)Math.min((long)nextWidth*nextHeight, (long)pixel + Math.min(maxPixels, 4096));
            downsample(levels[level-1], width, height, levels[level], nextWidth, nextHeight, srgb, alphaWeighted, pixel, end);
            pixel = end;
            if (pixel == nextWidth*nextHeight) {
                levels[level].flip(); level++; pixel = 0; width = nextWidth; height = nextHeight;
            }
            return level == levels.length;
        }

        /** Transfers the completed buffers to the caller; repeated access returns the same array. */
        public ByteBuffer[] result() {
            if (level != levels.length) throw new FdxException("Mip preparation is still pending");
            return levels;
        }
    }

    private static void downsample(ByteBuffer source, int width, int height, ByteBuffer out,
            int nextWidth, int nextHeight, boolean srgb, boolean alphaWeighted, int start, int end) {
        for (int pixel = start; pixel < end; pixel++) {
            int x = pixel % nextWidth, y = pixel / nextWidth;
            double x0 = (double)x*width/nextWidth, x1 = (double)(x+1)*width/nextWidth;
            double y0 = (double)y*height/nextHeight, y1 = (double)(y+1)*height/nextHeight;
            double red = 0, green = 0, blue = 0, alpha = 0, weight = 0, colorWeight = 0;
            for (int sy = (int)y0; sy < (int)Math.ceil(y1); sy++) {
                double dy = Math.min(y1, sy+1)-Math.max(y0, sy);
                for (int sx = (int)x0; sx < (int)Math.ceil(x1); sx++) {
                    double area = dy*(Math.min(x1, sx+1)-Math.max(x0, sx));
                    int index = (sy*width+sx)*4;
                    double a = (source.get(index+3)&255)/255.0;
                    double colorArea = alphaWeighted ? area*a : area;
                    red += decode(source.get(index), srgb)*colorArea;
                    green += decode(source.get(index+1), srgb)*colorArea;
                    blue += decode(source.get(index+2), srgb)*colorArea;
                    alpha += a*area;
                    weight += area;
                    colorWeight += colorArea;
                }
            }
            double inverse = colorWeight > 0 ? 1/colorWeight : 0;
            out.put(encode(red*inverse, srgb)).put(encode(green*inverse, srgb)).put(encode(blue*inverse, srgb));
            out.put((byte)Math.round(alpha/weight*255));
        }
    }

    private static double decode(byte value, boolean srgb) {
        float channel = (value&255)/255f;
        return srgb ? ColorTransfer.srgbToLinear(channel) : channel;
    }

    private static byte encode(double value, boolean srgb) {
        float channel = (float)Math.max(0, Math.min(1, value));
        if (srgb) channel = ColorTransfer.linearToSrgb(channel);
        return (byte)Math.round(channel*255);
    }
}
