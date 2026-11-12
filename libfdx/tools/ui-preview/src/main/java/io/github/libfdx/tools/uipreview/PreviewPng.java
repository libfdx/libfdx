package io.github.libfdx.tools.uipreview;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Direct PNG encoding; no ImageIO service/plugin discovery. Explicit captures may allocate. */
final class PreviewPng {
    static void write(Path path, int width, int height, ByteBuffer rgba) throws IOException {
        write(path, width, height, rgba, 0);
    }

    static void write(Path path, int width, int height, ByteBuffer rgba, int left) throws IOException {
        if (left < 0 || width <= left || height <= 0 || (long)width * height * 4 > rgba.limit()) {
            throw new IllegalArgumentException("Invalid RGBA frame dimensions");
        }
        int imageWidth = width - left;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream pixels = new DeflaterOutputStream(compressed)) {
            byte[] row = new byte[Math.multiplyExact(imageWidth, 4) + 1];
            for (int y = height - 1; y >= 0; y--) {
                row[0] = 0;
                rgba.get((y * width + left) * 4, row, 1, row.length - 1);
                pixels.write(row);
            }
        }
        ByteArrayOutputStream header = new ByteArrayOutputStream(13);
        try (DataOutputStream data = new DataOutputStream(header)) {
            data.writeInt(imageWidth); data.writeInt(height);
            data.write(new byte[] {8, 6, 0, 0, 0});
        }
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        try (DataOutputStream png = new DataOutputStream(Files.newOutputStream(absolute))) {
            png.write(new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10});
            chunk(png, "IHDR", header.toByteArray());
            chunk(png, "IDAT", compressed.toByteArray());
            chunk(png, "IEND", new byte[0]);
        }
    }

    private static void chunk(DataOutputStream output, String type, byte[] bytes) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(bytes);
        output.writeInt(bytes.length); output.write(name); output.write(bytes); output.writeInt((int)crc.getValue());
    }

}
