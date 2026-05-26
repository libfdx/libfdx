package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.tests.TestFpsLogger;

public final class SpriteBatchTest extends ApplicationAdapter {
    private static final String PLAYER_ASSET = "player.png";
    private static final int PLAYER_FRAME_WIDTH = 256;
    private static final int PLAYER_FRAME_HEIGHT = 256;

    private final long exitAfterFrames;
    private Application application;
    private AssetManager assets;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private TextureRegion[][] playerFrames;
    private boolean created;
    private long renderedFrames;

    public SpriteBatchTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        GraphicsContext graphics = fdx.graphics().main();
        assets = new DefaultAssetManager(fdx.files());
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "SpriteBatchTest");
        G2DAssetLoaders.register(assets, graphics);
        batch = new SpriteBatch(graphics);

        assets.load(AssetDescriptor.of(PLAYER_ASSET, Texture.class));
        assets.finishLoading();
        Texture player = assets.get(PLAYER_ASSET, Texture.class);
        playerFrames = TextureRegion.split(player, PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT);

        created = true;
        logger.info("SpriteBatchTest created with " + player.width() + "x" + player.height()
                + " player texture and " + frameCount() + " regions");
    }

    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        assets.update();
        batch.begin(LoadOp.clear(1.0f, 1.0f, 1.0f, 1.0f));
        batch.draw(frame(0, 0), -0.90f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(1, 1), -0.42f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(2, 0), 0.06f, -0.25f, 0.35f, 0.35f);
        batch.draw(frame(3, 2), 0.54f, -0.25f, 0.35f, 0.35f);
        batch.end();

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

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
            throw new FdxException("SpriteBatchTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("SpriteBatchTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("SpriteBatchTest rendered " + renderedFrames + " frames");
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
