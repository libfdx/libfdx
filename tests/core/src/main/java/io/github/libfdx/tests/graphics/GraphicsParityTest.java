package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class GraphicsParityTest extends ApplicationAdapter {
    protected final long exitAfterFrames;
    protected Application application;
    protected Display display;
    protected GraphicsContext graphics;
    protected Logger logger;

    private TestFpsLogger fpsLogger;
    private String testName;
    private String capturePath = "";
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    GraphicsParityTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    protected final void initialize(Fdx fdx, String testName) {
        this.testName = testName;
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, testName);
        capturePath = stringProperty("libfdx.test.capture", "");
        captureFrame = longProperty("libfdx.test.captureFrame", 2L);
    }

    protected final void markCreated() {
        created = true;
        logger.info(testName + " created for graphics provider " + graphics.providerId().value());
    }

    protected final void finishFrame() {
        if (capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame();
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(application.deltaTime(), renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    protected final void verifyDisposed() {
        if (!created) {
            throw new FdxException(testName + " did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException(testName + " rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath.length() > 0 && !captured) {
            throw new FdxException(testName + " did not capture framebuffer to " + capturePath);
        }
        logger.info(testName + " rendered " + renderedFrames + " frames");
    }

    protected final int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    protected final int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    protected static ByteBuffer floats(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < values.length; i++) {
            buffer.putFloat(values[i]);
        }
        buffer.flip();
        return buffer;
    }

    protected static ByteBuffer shorts(short[] values) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < values.length; i++) {
            buffer.putShort(values[i]);
        }
        buffer.flip();
        return buffer;
    }

    protected static ByteBuffer rgba8(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    }

    protected static int intProperty(String name, int defaultValue, int minimum) {
        String value = stringProperty(name, "");
        if (value.length() == 0) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        return parsed < minimum ? minimum : parsed;
    }

    protected static void dispose(Disposable disposable) {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void captureFrame() {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(capturePath, framebufferWidth(), framebufferHeight(), pixels);
            logger.info(testName + " captured framebuffer to " + capturePath);
        } catch (Exception e) {
            throw new FdxException("Could not capture " + testName + " framebuffer", e);
        }
    }

    private static String stringProperty(String name, String defaultValue) {
        String value = System.getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        return value.trim();
    }

    private static long longProperty(String name, long defaultValue) {
        String value = stringProperty(name, "");
        return value.length() == 0 ? defaultValue : Long.parseLong(value);
    }
}
