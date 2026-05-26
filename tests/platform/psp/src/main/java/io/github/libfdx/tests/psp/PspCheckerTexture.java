package io.github.libfdx.tests.psp;

import java.nio.ByteBuffer;

final class PspCheckerTexture {
    private PspCheckerTexture() {
    }

    static ByteBuffer pixels(int size, int cellSize, int borderSize) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean border = x < borderSize || y < borderSize || x >= size - borderSize
                        || y >= size - borderSize;
                boolean checker = ((x / cellSize) + (y / cellSize)) % 2 == 0;
                if (border) {
                    putRgba(pixels, 236, 72, 64, 255);
                } else if (checker) {
                    putRgba(pixels, 255, 255, 255, 255);
                } else {
                    putRgba(pixels, 24, 116, 220, 255);
                }
            }
        }
        pixels.flip();
        return pixels;
    }

    private static void putRgba(ByteBuffer pixels, int red, int green, int blue, int alpha) {
        pixels.put((byte) red);
        pixels.put((byte) green);
        pixels.put((byte) blue);
        pixels.put((byte) alpha);
    }
}
