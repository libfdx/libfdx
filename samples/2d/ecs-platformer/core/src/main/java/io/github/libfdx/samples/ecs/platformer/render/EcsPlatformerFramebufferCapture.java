package io.github.libfdx.samples.ecs.platformer.render;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public final class EcsPlatformerFramebufferCapture {
    private EcsPlatformerFramebufferCapture() {
    }

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

    private static byte[] ppmBytes(int width, int height, ByteBuffer pixels) throws Exception {
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

    private static String ppmHeader(int width, int height) {
        return "P6\n" + width + " " + height + "\n255\n";
    }
}
