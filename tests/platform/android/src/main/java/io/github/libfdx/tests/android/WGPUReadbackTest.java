package io.github.libfdx.tests.android;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import java.nio.ByteBuffer;

/** Verifies readback capability, safe rejection, and continued native submission on Android WGPU. */
public final class WGPUReadbackTest extends ApplicationAdapter {
    private GraphicsContext graphics;
    private Application application;
    private boolean expectedReadback;
    private int frames;

    @Override
    public void create(Fdx fdx) {
        graphics = fdx.graphics().main();
        application = fdx.app();
        expectedReadback = !System.getProperty("libfdx.test.capture", "").isBlank();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        FrameBuffer buffer = frame.frameBuffer();
        require(buffer.supportsReadPixelsRgba8() == expectedReadback, "Incorrect readback capability");
        graphics.clear(0, 1, 0, 1);
        if (expectedReadback) {
            ByteBuffer pixels = buffer.readPixelsRgba8();
            require(pixels.position() == 0 && pixels.remaining() == buffer.width() * buffer.height() * 4,
                    "Incorrect readback byte count");
            for (int i = 0; i < pixels.limit(); i += 4) {
                require((pixels.get(i) & 255) == 0 && (pixels.get(i + 1) & 255) == 255
                        && (pixels.get(i + 2) & 255) == 0 && (pixels.get(i + 3) & 255) == 255,
                        "Readback does not contain the rendered green frame");
            }
        } else {
            try {
                // Deliberately bypass the query to verify protection for incorrect callers.
                buffer.readPixelsRgba8();
                throw new AssertionError("Unsupported readback was accepted");
            } catch (UnsupportedOperationException expected) {
                require(expected.getMessage().contains("CopySrc"), "Missing actionable rejection reason");
            }
            require(graphics.currentFrame() == frame, "Rejected readback consumed the frame");
            graphics.clear(0, 1, 0, 1);
        }
        if (++frames == 8) {
            System.out.println("WGPU_READBACK_PASS supported=" + expectedReadback + " frames=" + frames
                    + " rejection_preserves_frame=" + !expectedReadback);
            application.requestExit();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
