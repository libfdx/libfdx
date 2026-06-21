package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.SpriteOutlineRenderer2D;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the 2D sprite outline shader test scenario.
 *
 * @author xpenatan
 */
public final class Outline2DTest extends ApplicationAdapter {
    private static final int SPRITE_SIZE = 64;
    private static final int CENTER = SPRITE_SIZE / 2;
    private static final int DIAMOND_RADIUS = 20;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private SpriteOutlineRenderer2D outlineRenderer;
    private Texture spriteTexture;
    private TextureRegion spriteRegion;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates an outline2 d test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Outline2DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "Outline2DTest");
        outlineRenderer = new SpriteOutlineRenderer2D(graphics);
        outlineRenderer.color(1.0f, 0.35f, 0.16f, 1.0f);
        outlineRenderer.outlineColor(0.0f, 0.82f, 1.0f, 1.0f);
        outlineRenderer.outlineWidth(2.5f);
        spriteTexture = createSpriteTexture();
        spriteRegion = new TextureRegion(spriteTexture);
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        created = true;
        logger.info("Outline2DTest created WGSL sprite outline renderer for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        outlineRenderer.begin(LoadOp.clear(0.025f, 0.03f, 0.045f, 1.0f));
        outlineRenderer.draw(spriteRegion, -0.42f, -0.42f, 0.84f, 0.84f);
        outlineRenderer.end();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (outlineRenderer != null) {
            outlineRenderer.dispose();
            outlineRenderer = null;
        }
        if (spriteTexture != null) {
            spriteTexture.dispose();
            spriteTexture = null;
        }
        if (!created) {
            throw new FdxException("Outline2DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Outline2DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("Outline2DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Outline2DTest rendered " + renderedFrames + " frames");
    }

    private Texture createSpriteTexture() {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("outline 2d sprite",
                SPRITE_SIZE, SPRITE_SIZE));
        graphics.device().writeTexture(texture, spritePixels());
        return texture;
    }

    private ByteBuffer spritePixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(SPRITE_SIZE * SPRITE_SIZE * 4);
        for (int y = 0; y < SPRITE_SIZE; y++) {
            for (int x = 0; x < SPRITE_SIZE; x++) {
                int distance = Math.abs(x - CENTER) + Math.abs(y - CENTER);
                if (distance <= DIAMOND_RADIUS) {
                    boolean highlight = distance < DIAMOND_RADIUS / 2 || x == CENTER || y == CENTER;
                    putRgba(pixels, highlight ? 0xFF8A3DFF : 0xD84315FF);
                } else {
                    putRgba(pixels, 0x00000000);
                }
            }
        }
        pixels.flip();
        return pixels;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("Outline2DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Outline2DTest framebuffer", e);
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

    private void putRgba(ByteBuffer pixels, int rgba) {
        pixels.put((byte)((rgba >>> 24) & 0xFF));
        pixels.put((byte)((rgba >>> 16) & 0xFF));
        pixels.put((byte)((rgba >>> 8) & 0xFF));
        pixels.put((byte)(rgba & 0xFF));
    }
}
