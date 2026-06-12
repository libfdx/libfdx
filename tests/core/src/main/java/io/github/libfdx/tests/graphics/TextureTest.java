package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the texture test scenario.
 *
 * @author xpenatan
 */
public final class TextureTest extends ApplicationAdapter {
    private static final String LOGO_ASSET = "fdx_logo_dark.png";
    private static final float MAX_WIDTH = 1.55f;
    private static final float MAX_HEIGHT = 1.20f;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private AssetManager assets;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private Texture logo;
    private String capturePath;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a texture test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public TextureTest(long exitAfterFrames) {
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
        assets = new DefaultAssetManager(fdx.files());
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "TextureTest");
        G2DAssetLoaders.register(assets, graphics);
        batch = new SpriteBatch(graphics);
        capturePath = System.getProperty("libfdx.test.capture", "").trim();

        assets.load(AssetDescriptor.of(LOGO_ASSET, Texture.class));
        assets.finishLoading();
        logo = assets.get(LOGO_ASSET, Texture.class);

        created = true;
        logger.info("TextureTest created with " + logo.width() + "x" + logo.height()
                + " texture and SpriteBatch");
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        assets.update();
        float height = textureHeight();
        float width = textureWidth(height);
        float x = -width * 0.5f;
        float y = -height * 0.5f;

        batch.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
        batch.draw(logo, x, y, width, height);
        batch.end();

        if (!captured && capturePath.length() > 0) {
            captureFrame();
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
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (!created) {
            throw new FdxException("TextureTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("TextureTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("TextureTest did not capture framebuffer to " + capturePath);
        }
        logger.info("TextureTest rendered " + renderedFrames + " frames");
    }

    private void captureFrame() {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(capturePath, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("TextureTest captured framebuffer to " + capturePath);
        } catch (Exception e) {
            throw new FdxException("Could not capture TextureTest framebuffer", e);
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

    private float textureWidth(float height) {
        float displayAspect = displayAspect();
        float textureAspect = logo.width() / (float) logo.height();
        float width = height * textureAspect / displayAspect;
        return width <= MAX_WIDTH ? width : MAX_WIDTH;
    }

    private float textureHeight() {
        float displayAspect = displayAspect();
        float textureAspect = logo.width() / (float) logo.height();
        float widthAtMaxHeight = MAX_HEIGHT * textureAspect / displayAspect;
        if (widthAtMaxHeight <= MAX_WIDTH) {
            return MAX_HEIGHT;
        }
        return MAX_WIDTH * displayAspect / textureAspect;
    }

    private float displayAspect() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        if (width <= 0 || height <= 0) {
            return 4.0f / 3.0f;
        }
        return width / (float) height;
    }
}
