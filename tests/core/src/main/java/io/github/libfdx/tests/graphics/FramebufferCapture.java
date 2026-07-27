package io.github.libfdx.tests.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Represents a framebuffer capture.
 *
 * @author xpenatan
 */
public final class FramebufferCapture {
    private FramebufferCapture() {
    }

    /**
     * Runs the read pixels RGBA8 step.
     *
     * @param graphics the graphics context
     * @return the read pixels RGBA8
     */
    public static ByteBuffer readPixelsRgba8(GraphicsContext graphics) {
        return graphics.currentFrame().frameBuffer().readPixelsRgba8();
    }

    /**
     * Validates that a captured scene frame contains readable, visible, non-uniform color data.
     *
     * <p>This is intentionally a coarse runtime guard for rendered regression scenarios. It catches
     * missing/truncated readback, fully transparent output, black output, and a uniform clear-only
     * frame. Pixel-reference comparison remains responsible for proving exact visual behavior.</p>
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param pixels the RGBA8 pixels
     * @throws FdxException if the frame is invalid or does not contain visible scene variation
     */
    public static void validateSceneFrame(int width, int height, ByteBuffer pixels) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("Captured scene frame dimensions must be greater than zero");
        }
        if (pixels == null) {
            throw new FdxException("Captured scene frame pixels cannot be null");
        }
        long pixelCountLong = (long)width * height;
        long byteCountLong = pixelCountLong * 4L;
        if (byteCountLong > Integer.MAX_VALUE || pixels.limit() < byteCountLong) {
            throw new FdxException("Captured scene frame does not contain the required RGBA8 pixels");
        }

        int pixelCount = (int)pixelCountLong;
        int firstRed = pixels.get(0) & 0xFF;
        int firstGreen = pixels.get(1) & 0xFF;
        int firstBlue = pixels.get(2) & 0xFF;
        int differentPixels = 0;
        int visiblePixels = 0;
        int minimumChannel = 255;
        int maximumChannel = 0;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int offset = pixel * 4;
            int red = pixels.get(offset) & 0xFF;
            int green = pixels.get(offset + 1) & 0xFF;
            int blue = pixels.get(offset + 2) & 0xFF;
            int alpha = pixels.get(offset + 3) & 0xFF;
            minimumChannel = Math.min(minimumChannel, Math.min(red, Math.min(green, blue)));
            maximumChannel = Math.max(maximumChannel, Math.max(red, Math.max(green, blue)));
            if (alpha > 0) {
                visiblePixels++;
            }
            if (red != firstRed || green != firstGreen || blue != firstBlue) {
                differentPixels++;
            }
        }

        int minimumEvidencePixels = Math.min(64, Math.max(1, pixelCount / 4096));
        if (visiblePixels < minimumEvidencePixels) {
            throw new FdxException("Captured scene frame is fully transparent or has insufficient visible pixels");
        }
        if (maximumChannel <= 8) {
            throw new FdxException("Captured scene frame is black");
        }
        if (maximumChannel - minimumChannel < 4 || differentPixels < minimumEvidencePixels) {
            throw new FdxException("Captured scene frame is uniform and appears to contain only a clear color");
        }
    }

    /**
     * Runs the write ppm step.
     *
     * @param path the asset or file path
     * @param width the width in pixels
     * @param height the height in pixels
     * @param pixels the pixels
     * @throws Exception if the operation cannot be completed
     */
    public static void writePpm(String path, int width, int height, ByteBuffer pixels) throws Exception {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(path);
        try {
            out.write(ppmBytes(width, height, pixels));
        } finally {
            out.close();
        }
    }

    /**
     * Runs the ppm bytes step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param pixels the pixels
     * @return the ppm bytes
     * @throws Exception if the operation cannot be completed
     */
    public static byte[] ppmBytes(int width, int height, ByteBuffer pixels) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(ppmHeader(width, height).length()
                + width * height * 3);
        out.write(ppmHeader(width, height).getBytes("US-ASCII"));
        byte[] row = new byte[width * 3];
        for (int y = height - 1; y >= 0; y--) {
            int rowOffset = y * width * 4;
            int outOffset = 0;
            for (int x = 0; x < width; x++) {
                int pixelOffset = rowOffset + x * 4;
                row[outOffset++] = pixels.get(pixelOffset);
                row[outOffset++] = pixels.get(pixelOffset + 1);
                row[outOffset++] = pixels.get(pixelOffset + 2);
            }
            out.write(row);
        }
        return out.toByteArray();
    }

    /**
     * Runs the ppm header step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the ppm header
     */
    public static String ppmHeader(int width, int height) {
        return "P6\n" + width + " " + height + "\n255\n";
    }
}
