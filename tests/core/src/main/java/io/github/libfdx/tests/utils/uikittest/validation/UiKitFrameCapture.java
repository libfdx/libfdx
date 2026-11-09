package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFrame;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Pixel readback and PNG IO for the UI Kit validation scenarios. */
final class UiKitFrameCapture {
    private UiKitFrameCapture() {
    }

    public record CapturedFrame(int width, int height, int[] argb) { }

    public static CapturedFrame capture(GraphicsFrame frame) {
        int width = frame.width();
        int height = frame.height();
        ByteBuffer pixels = frame.frameBuffer().readPixelsRgba8();
        int[] image = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int offset = (sourceY * width + x) * 4;
                int red = pixels.get(offset) & 0xff;
                int green = pixels.get(offset + 1) & 0xff;
                int blue = pixels.get(offset + 2) & 0xff;
                int alpha = pixels.get(offset + 3) & 0xff;
                image[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        return new CapturedFrame(width, height, image);
    }

    public static CapturedFrame readImage(File file, String safeScenario) {
        try {
            Class<?> imageIoClass = Class.forName("javax.imageio.ImageIO");
            Object buffered = imageIoClass.getMethod("read", File.class).invoke(null, file);
            if (buffered == null) {
                throw new FdxException("UiKitTest read invalid baseline image for scenario " + safeScenario);
            }
            Class<?> imageClass = buffered.getClass();
            int width = ((Integer) imageClass.getMethod("getWidth").invoke(buffered)).intValue();
            int height = ((Integer) imageClass.getMethod("getHeight").invoke(buffered)).intValue();
            int[] argb = (int[]) imageClass
                    .getMethod("getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class)
                    .invoke(buffered, 0, 0, width, height, null, 0, width);
            return new CapturedFrame(width, height, argb);
        } catch (FdxException e) {
            throw e;
        } catch (Exception e) {
            throw new FdxException("UiKitTest could not read baseline for scenario " + safeScenario, e);
        }
    }

    public static void writeDesktopPng(CapturedFrame image, File output) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream((image.width * 4 + 1) * image.height);
        for (int y = 0; y < image.height; y++) {
            raw.write(0);
            int row = y * image.width;
            for (int x = 0; x < image.width; x++) {
                int argb = image.argb[row + x];
                raw.write((argb >>> 16) & 0xff);
                raw.write((argb >>> 8) & 0xff);
                raw.write(argb & 0xff);
                raw.write((argb >>> 24) & 0xff);
            }
        }

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        byte[] rawBytes = raw.toByteArray();
        deflater.setInput(rawBytes);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(rawBytes.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressed.write(buffer, 0, count);
        }
        deflater.end();

        ByteArrayOutputStream ihdrBytes = new ByteArrayOutputStream(13);
        DataOutputStream ihdr = new DataOutputStream(ihdrBytes);
        ihdr.writeInt(image.width);
        ihdr.writeInt(image.height);
        ihdr.writeByte(8);
        ihdr.writeByte(6);
        ihdr.writeByte(0);
        ihdr.writeByte(0);
        ihdr.writeByte(0);
        ihdr.flush();

        DataOutputStream png = new DataOutputStream(new FileOutputStream(output));
        try {
            png.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            writePngChunk(png, "IHDR", ihdrBytes.toByteArray());
            writePngChunk(png, "IDAT", compressed.toByteArray());
            writePngChunk(png, "IEND", new byte[0]);
        } finally {
            png.close();
        }
    }

    private static void writePngChunk(DataOutputStream png, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        png.writeInt(data.length);
        png.write(typeBytes);
        png.write(data);
        png.writeInt((int) crc.getValue());
    }

}
