package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.AssetStatus;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatch;

final class PspBackendSpriteBatchTest extends ApplicationAdapter {
    private static final String LOGO_ASSET = "fdx.png";
    private static final int LOADING_TEXTURE_SIZE = 128;
    private static final long LOGO_QUEUE_DELAY_FRAMES = 2L;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private FileSystem files;
    private GraphicsContext graphics;
    private AssetManager assets;
    private AssetHandle<Texture> logoHandle;
    private SpriteBatch spriteBatch;
    private Texture loadingTexture;
    private Texture logo;
    private boolean logoQueued;
    private boolean loadLogged;
    private boolean loadFailed;
    private long renderedFrames;

    PspBackendSpriteBatchTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        logger = fdx.logger();
        files = fdx.files();
        graphics = fdx.graphics().main();
        spriteBatch = new SpriteBatch(graphics, 6);
        loadingTexture = graphics.device().createTexture(TextureDescriptor.rgba8(
                "psp backend loading", LOADING_TEXTURE_SIZE, LOADING_TEXTURE_SIZE));
        graphics.device().writeTexture(loadingTexture, PspCheckerTexture.pixels(LOADING_TEXTURE_SIZE, 16, 4));
        logger.info("PspBackendSpriteBatchTest initialized backend SpriteBatch loading texture");
    }

    @Override
    public void render() {
        if (!logoQueued && renderedFrames >= LOGO_QUEUE_DELAY_FRAMES) {
            queueLogo();
        }
        if (logoQueued) {
            updateAsset();
        }

        float red = loadFailed ? 0.72f : 1.0f;
        float green = loadFailed ? 0.02f : 1.0f;
        float blue = loadFailed ? 0.02f : 1.0f;
        Texture texture = logo != null ? logo : loadingTexture;
        float size = logo != null ? 1.44f : 0.72f;
        float position = -size * 0.5f;
        spriteBatch.begin(LoadOp.clear(red, green, blue, 1.0f));
        spriteBatch.viewport(display.framebufferWidth(), display.framebufferHeight());
        spriteBatch.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (logo != null) {
            spriteBatch.draw(texture, -0.72f, -0.72f, 1.44f, 1.44f);
        } else if (texture != null) {
            spriteBatch.draw(texture, position, position, size, size);
        }
        spriteBatch.end();

        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    private void queueLogo() {
        assets = new DefaultAssetManager(files);
        G2DAssetLoaders.register(assets, graphics);
        logoHandle = assets.load(AssetDescriptor.of(LOGO_ASSET, Texture.class));
        logoQueued = true;
        logger.info("PspBackendSpriteBatchTest queued " + LOGO_ASSET + " through fdx.files()");
    }

    private void updateAsset() {
        if (logo != null || loadFailed || assets == null || logoHandle == null) {
            return;
        }
        assets.update();
        if (logoHandle.isLoaded()) {
            logo = logoHandle.asset();
            if (!loadLogged && logo != null) {
                loadLogged = true;
                logger.info("PspBackendSpriteBatchTest loaded " + logo.width() + "x" + logo.height()
                        + " texture through fdx.files()");
            }
        } else if (logoHandle.status() == AssetStatus.FAILED) {
            loadFailed = true;
            logger.error("PspBackendSpriteBatchTest failed to load " + LOGO_ASSET);
        }
    }

    @Override
    public void dispose() {
        if (spriteBatch != null) {
            spriteBatch.dispose();
            spriteBatch = null;
        }
        if (loadingTexture != null) {
            loadingTexture.dispose();
            loadingTexture = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        logger.info("PspBackendSpriteBatchTest rendered " + renderedFrames + " frames");
    }
}
