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
import io.github.libfdx.graphics.g2d.SpriteBatchConfig;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.StandardSpriteTechnique;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the sprite batch test scenario.
 *
 * @author xpenatan
 */
public final class SpriteBatchTest extends ApplicationAdapter {
    private static final String PLAYER_ASSET = "player.png";
    private static final int PLAYER_FRAME_WIDTH = 256;
    private static final int PLAYER_FRAME_HEIGHT = 256;
    private static final float[] GRAPH_CENTERS_X = {
            -0.55f, 0.0f, 0.55f
    };
    private static final float[] GRAPH_CENTERS_Y = {
            0.45f, 0.45f, 0.45f
    };

    private final long exitAfterFrames;
    private final boolean graphShaders;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private AssetManager assets;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private ShaderGraphProvider graphProvider;
    private TextureRegion[][] playerFrames;
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
        this(exitAfterFrames, false);
    }

    /**
     * Creates a sprite batch test.
     *
     * @param exitAfterFrames the exit after frames
     * @param graphShaders whether to use the standard graph technique
     */
    public SpriteBatchTest(long exitAfterFrames, boolean graphShaders) {
        this.exitAfterFrames = exitAfterFrames;
        this.graphShaders = graphShaders;
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
        fpsLogger = TestFpsLogger.create(logger, "SpriteBatchTest");
        G2DAssetLoaders.register(assets, graphics);
        if (graphShaders) {
            graphProvider = new ShaderGraphProvider(graphics,
                    StandardSpriteTechnique.compile(graphics));
            batch = new SpriteBatch(graphics,
                    new SpriteBatchConfig()
                            .shaderProvider(graphProvider));
        } else {
            batch = new SpriteBatch(graphics);
        }

        assets.load(AssetDescriptor.of(PLAYER_ASSET, Texture.class));
        assets.finishLoading();
        Texture player = assets.get(PLAYER_ASSET, Texture.class);
        playerFrames = TextureRegion.split(player, PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT);
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));

        created = true;
        logger.info("SpriteBatchTest created with " + player.width() + "x" + player.height()
                + " player texture and " + frameCount() + " regions");
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        assets.update();
        batch.begin(LoadOp.clear(1.0f, 1.0f, 1.0f, 1.0f));
        batch.draw(frame(0, 0), -0.90f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(1, 1), -0.42f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(2, 0), 0.06f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(3, 2), 0.54f, -0.25f, 0.35f, 0.35f);
        if (graphShaders) {
            batch.draw(frame(0, 0), GRAPH_CENTERS_X,
                    GRAPH_CENTERS_Y, GRAPH_CENTERS_X.length,
                    0.24f, 0.24f, 0.12f, 0.12f, 0.0f);
        }
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
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (graphProvider != null) {
            graphProvider.dispose();
            graphProvider = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
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
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("SpriteBatchTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture SpriteBatchTest framebuffer", e);
        }
    }

    private TextureRegion frame(int row, int column) {
        if (row >= 0 && row < playerFrames.length && column >= 0 && column < playerFrames[row].length) {
            return playerFrames[row][column];
        }
        return playerFrames[0][0];
    }

    private int frameCount() {
        int count = 0;
        for (int row = 0; row < playerFrames.length; row++) {
            count += playerFrames[row].length;
        }
        return count;
    }
}
