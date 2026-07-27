package io.github.libfdx.tests.graphics;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FramebufferCaptureTest {
    @Test
    void acceptsVisibleNonUniformSceneData() {
        ByteBuffer pixels = solidFrame(8, 8, 16, 20, 24, 255);
        for (int pixel = 0; pixel < 16; pixel++) {
            int offset = pixel * 4;
            pixels.put(offset, (byte)(80 + pixel));
            pixels.put(offset + 1, (byte)(110 + pixel));
            pixels.put(offset + 2, (byte)(140 + pixel));
        }

        assertDoesNotThrow(() -> FramebufferCapture.validateSceneFrame(8, 8, pixels));
    }

    @Test
    void rejectsBlackUniformAndTruncatedFrames() {
        assertThrows(FdxException.class,
                () -> FramebufferCapture.validateSceneFrame(8, 8, solidFrame(8, 8, 0, 0, 0, 255)));
        assertThrows(FdxException.class,
                () -> FramebufferCapture.validateSceneFrame(8, 8, solidFrame(8, 8, 40, 44, 48, 255)));
        assertThrows(FdxException.class,
                () -> FramebufferCapture.validateSceneFrame(8, 8, ByteBuffer.allocateDirect(8)));
    }

    private static ByteBuffer solidFrame(int width, int height, int red, int green, int blue, int alpha) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int pixel = 0; pixel < width * height; pixel++) {
            pixels.put((byte)red);
            pixels.put((byte)green);
            pixels.put((byte)blue);
            pixels.put((byte)alpha);
        }
        pixels.flip();
        return pixels;
    }
}
