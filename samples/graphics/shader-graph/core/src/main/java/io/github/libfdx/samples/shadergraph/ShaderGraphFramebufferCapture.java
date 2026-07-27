package io.github.libfdx.samples.shadergraph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Writes deterministic sample captures without depending on test modules.
 */
public final class ShaderGraphFramebufferCapture {
    private ShaderGraphFramebufferCapture() {
    }

    /**
     * Writes bottom-left-origin RGBA8 pixels as a top-left-origin PPM image.
     *
     * @param path output path
     * @param width image width
     * @param height image height
     * @param pixels RGBA8 pixels
     * @throws Exception when the output cannot be written
     */
    public static void writePpm(String path, int width, int height,
            ByteBuffer pixels) throws Exception {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(ppmBytes(width, height, pixels));
        }
    }

    private static byte[] ppmBytes(int width, int height,
            ByteBuffer pixels) throws Exception {
        String header = "P6\n" + width + " " + height + "\n255\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                header.length() + width * height * 3);
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        byte[] row = new byte[width * 3];
        for (int y = height - 1; y >= 0; y--) {
            int source = y * width * 4;
            int destination = 0;
            for (int x = 0; x < width; x++) {
                row[destination++] = pixels.get(source);
                row[destination++] = pixels.get(source + 1);
                row[destination++] = pixels.get(source + 2);
                source += 4;
            }
            output.write(row);
        }
        return output.toByteArray();
    }
}
