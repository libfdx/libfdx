package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
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
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g2d.TextureLoadOptions;
import io.github.libfdx.testsupport.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Renders an animated island harbor with layered, tinted texture-region sprites.
 *
 * @author xpenatan
 */
public final class SpriteBatchTest extends ApplicationAdapter {
    private static final String COAST_ASSET = "tiled/images/coast.png";
    private static final int TILE_SIZE = 32;
    private static final float SCENE_WIDTH = 960;
    private static final float SCENE_HEIGHT = 600;
    private static final LoadOp SEA_CLEAR = LoadOp.clear(32 / 255f, 106 / 255f, 132 / 255f, 1);

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private AssetManager assets;
    private Runnable assetSetup;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private TextureRegion[] sprites;
    private float elapsed;
    private float scaleX;
    private float scaleY;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a sprite batch test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public SpriteBatchTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        assets = new DefaultAssetManager(fdx.files());
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, getClass().getSimpleName());
        G2DAssetLoaders.register(assets, graphics);
        batch = new SpriteBatch(graphics);
        assets.load(TextureLoadOptions.PIXEL_ART.descriptor(COAST_ASSET, Texture.class));
        assetSetup = this::createLoadedAssets;
    }

    private void createLoadedAssets() {
        Texture coast = assets.get(COAST_ASSET, Texture.class);
        // This authored sheet contains twelve complete 32x32 sprites in one row.
        if (coast.width() != 12 * TILE_SIZE || coast.height() != TILE_SIZE) {
            throw new FdxException("Unexpected coastal sprite sheet dimensions");
        }
        sprites = TextureRegion.split(coast, TILE_SIZE, TILE_SIZE)[0];
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));

        created = true;
        logger.info("SpriteBatchTest created: animated island harbor");
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        boolean assetsFinished = assets.update(4, 1_000_000L);
        if (assetSetup != null) {
            if (!assetsFinished) {
                graphics.clear(0.02f, 0.025f, 0.04f, 1.0f);
                return;
            }
            Runnable setup = assetSetup;
            assetSetup = null;
            setup.run();
        }
        float deltaSeconds = application.deltaTime();
        elapsed += Math.max(0, Math.min(deltaSeconds, 0.1f));
        int width = framebufferWidth();
        int height = framebufferHeight();
        float scale = Math.min(width / SCENE_WIDTH, height / SCENE_HEIGHT);
        scaleX = 2 * scale / width;
        scaleY = 2 * scale / height;
        batch.viewport(width, height);
        batch.begin(SEA_CLEAR);
        drawHarbor();
        batch.end();

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
        boolean cancelledLoading = assetSetup != null;
        assetSetup = null;
        if (batch != null) {
            batch.dispose();
            batch = null;
        }

        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (cancelledLoading && exitAfterFrames == 0L) {
            return;
        }
        if (!created) {
            throw new FdxException("SpriteBatchTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("SpriteBatchTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("SpriteBatchTest did not capture framebuffer to " + capturePath);
        }
        logger.info("SpriteBatchTest rendered " + renderedFrames + " frames");
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.validateSceneFrame(framebufferWidth(), framebufferHeight(), pixels);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("SpriteBatchTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture SpriteBatchTest framebuffer", e);
        }
    }

    private void drawHarbor() {
        batch.color(1, 1, 1, 1);
        // Water extends beyond the fitted scene so wide/tall windows have no hard border.
        int waterFrame = (int) (elapsed * 2) % 2;
        int left = (int) Math.floor((SCENE_WIDTH / 2 - 1 / scaleX) / TILE_SIZE) * TILE_SIZE;
        int top = (int) Math.floor((SCENE_HEIGHT / 2 - 1 / scaleY) / TILE_SIZE) * TILE_SIZE;
        float right = SCENE_WIDTH / 2 + 1 / scaleX;
        float bottom = SCENE_HEIGHT / 2 + 1 / scaleY;
        for (int y = top; y < bottom; y += TILE_SIZE) {
            for (int x = left; x < right; x += TILE_SIZE) {
                sprite(waterFrame, x, y, TILE_SIZE, TILE_SIZE);
            }
        }
        // A sandy rim surrounds the grass; a path joins the cottages to the pier.
        for (int row = 0; row < 11; row++) {
            for (int column = 0; column < 21; column++) {
                float nx = (column - 10) / 10.5f;
                float ny = (row - 5) / 5.5f;
                float distance = nx * nx + ny * ny;
                if (distance > 1) continue;
                int tile = distance > 0.70f ? 2 : 3;
                if (distance < 0.70f && (row == 5 || (column == 10 && row > 5))) tile = 4;
                sprite(tile, 144 + column * 32, 124 + row * 32, 32, 32);
            }
        }
        for (int y = 444; y < 556; y += 28) sprite(7, 464, y, 32, 28);
        sprite(7, 432, 528, 32, 28);
        sprite(7, 496, 528, 32, 28);

        // Back-to-front submission gives the village a readable depth order.
        sprite(5, 350, 132, 64, 80);
        sprite(5, 430, 112, 72, 90);
        sprite(5, 530, 128, 64, 80);
        sprite(6, 298, 191, 96, 96);
        batch.color(0.86f, 0.94f, 1, 1);
        sprite(6, 530, 187, 96, 96);
        batch.color(1, 1, 1, 1);
        sprite(11, 687, 238, 80, 112);
        sprite(8, 740, 334, 48, 48);
        sprite(8, 202, 315, 56, 48);
        for (int i = 0; i < 7; i++) {
            sprite(9, 300 + i * 48, 308 + (i % 2) * 24, 32, 32);
        }
        sprite(5, 248, 315, 80, 100);
        sprite(5, 330, 360, 64, 80);
        sprite(5, 588, 341, 80, 100);
        sprite(5, 670, 332, 64, 80);

        // Whole cloud sprites overlap the island with alpha blending and gentle drift.
        batch.color(1, 1, 1, 0.65f);
        for (int i = 0; i < 4; i++) {
            float x = ((i * 283 + elapsed * (9 + i * 2)) % 1240) - 140;
            sprite(10, x, 45 + (i % 3) * 142, 160, 80);
        }
        batch.color(1, 1, 1, 1);
    }

    /** Fits a centered, y-down scene without stretching the sprites on resize. */
    private void sprite(int index, float x, float y, float width, float height) {
        batch.draw(sprites[index], (x - SCENE_WIDTH / 2) * scaleX,
                (SCENE_HEIGHT / 2 - y - height) * scaleY, width * scaleX, height * scaleY);
    }
}
