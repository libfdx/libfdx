package io.github.libfdx.tests.graphics;

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
