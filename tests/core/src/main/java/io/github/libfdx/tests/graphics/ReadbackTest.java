package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;
import io.github.libfdx.tests.TestFpsLogger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

public final class ReadbackTest extends ApplicationAdapter {
    private static final int COLOR_TOLERANCE = 28;
    private static final String DEFAULT_CAPTURE_PATH = "build/libfdx-readback/readback-orientation.ppm";

    private final long exitAfterFrames;
    private Fdx fdx;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ShapeRenderer2D shapes;
    private boolean created;
    private boolean validated;
    private long renderedFrames;

    public ReadbackTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "ReadbackTest");
        shapes = new ShapeRenderer2D(graphics);

        created = true;
        logger.info("ReadbackTest created with graphics provider " + graphics.providerId());
    }

    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        shapes.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
        shapes.filledRect(-1.0f, 0.0f, 2.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f);
        shapes.filledRect(-1.0f, -1.0f, 2.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f);
        shapes.end();

        if (!validated) {
            validateReadback();
            validated = true;
        }

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (shapes != null) {
            shapes.dispose();
            shapes = null;
        }
        if (!created) {
            throw new FdxException("ReadbackTest did not create graphics resources");
        }
        if (!validated) {
            throw new FdxException("ReadbackTest did not validate framebuffer readback");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ReadbackTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("ReadbackTest rendered " + renderedFrames + " frames");
    }

    private void validateReadback() {
        int width = framebufferWidth();
        int height = framebufferHeight();
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int expectedSize = width * height * 4;
            if (pixels.limit() < expectedSize) {
                throw new FdxException("ReadbackTest expected at least " + expectedSize
                        + " readback bytes but received " + pixels.limit());
            }

            int x = width / 2;
            int bottomY = height / 4;
            int topY = height - 1 - height / 4;
            assertPixel(pixels, width, x, bottomY, 255, 0, 255, "bottom raw readback row");
            assertPixel(pixels, width, x, topY, 0, 255, 0, "top raw readback row");

            String capturePath = capturePath();
            if (isAndroidFiles()) {
                byte[] ppm = FramebufferCapture.ppmBytes(width, height, pixels);
                FileHandle capture = fdx.files().cache("libfdx-readback/readback-orientation.ppm");
                capture.writeBytes(ppm, false).get();
                validatePpmBytes(capture.path(), ppm, width, height, x);
                capturePath = capture.path();
            } else {
                FramebufferCapture.writePpm(capturePath, width, height, pixels);
                validatePpm(capturePath, width, height, x);
            }
            logger.info("ReadbackTest validated framebuffer readback and capture at " + capturePath);
        } catch (FdxException e) {
            throw e;
        } catch (Exception e) {
            throw new FdxException("Could not validate framebuffer readback", e);
        }
    }

    private String capturePath() {
        String configured = System.getProperty("libfdx.test.capture");
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        return DEFAULT_CAPTURE_PATH;
    }

    private boolean isAndroidFiles() {
        return fdx != null && "android-files".equals(fdx.files().providerId().value());
    }

    private void validatePpm(String path, int width, int height, int x) throws Exception {
        validatePpmBytes(path, readFile(path), width, height, x);
    }

    private void validatePpmBytes(String path, byte[] file, int width, int height, int x) throws Exception {
        byte[] header = FramebufferCapture.ppmHeader(width, height).getBytes("US-ASCII");
        int expectedSize = header.length + width * height * 3;
        if (file.length != expectedSize) {
            throw new FdxException("ReadbackTest expected " + expectedSize + " PPM bytes but found "
                    + file.length + " in " + path);
        }
        for (int i = 0; i < header.length; i++) {
            if (file[i] != header[i]) {
                throw new FdxException("ReadbackTest wrote an invalid PPM header to " + path);
            }
        }

        int topOffset = header.length + x * 3;
        int bottomOffset = header.length + (height - 1) * width * 3 + x * 3;
        assertRgb(file, topOffset, 0, 255, 0, "top PPM row");
        assertRgb(file, bottomOffset, 255, 0, 255, "bottom PPM row");
    }

    private byte[] readFile(String path) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            throw new FdxException("ReadbackTest did not create capture file " + path);
        }
        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            throw new FdxException("ReadbackTest capture file is too large: " + path);
        }
        byte[] bytes = new byte[(int)length];
        FileInputStream in = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset != bytes.length) {
                throw new FdxException("ReadbackTest could not read complete capture file " + path);
            }
        } finally {
            in.close();
        }
        return bytes;
    }

    private void assertPixel(ByteBuffer pixels, int width, int x, int y, int red, int green, int blue,
            String label) {
        int offset = (y * width + x) * 4;
        assertRgb(pixels.get(offset) & 0xff, pixels.get(offset + 1) & 0xff,
                pixels.get(offset + 2) & 0xff, red, green, blue, label);
    }

    private void assertRgb(byte[] bytes, int offset, int red, int green, int blue, String label) {
        assertRgb(bytes[offset] & 0xff, bytes[offset + 1] & 0xff, bytes[offset + 2] & 0xff,
                red, green, blue, label);
    }

    private void assertRgb(int actualRed, int actualGreen, int actualBlue, int red, int green, int blue,
            String label) {
        if (Math.abs(actualRed - red) > COLOR_TOLERANCE
                || Math.abs(actualGreen - green) > COLOR_TOLERANCE
                || Math.abs(actualBlue - blue) > COLOR_TOLERANCE) {
            throw new FdxException("ReadbackTest expected " + label + " to be rgb("
                    + red + ", " + green + ", " + blue + ") but found rgb("
                    + actualRed + ", " + actualGreen + ", " + actualBlue + ")");
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }
}
