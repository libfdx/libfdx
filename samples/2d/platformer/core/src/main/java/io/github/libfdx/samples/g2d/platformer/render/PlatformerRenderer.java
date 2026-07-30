package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.g2d.platformer.PlatformerConstants;
import io.github.libfdx.samples.g2d.platformer.PlatformerGame;

/**
 * Renders the application-owned platformer model from retained sample state.
 *
 * @author xpenatan
 */
public final class PlatformerRenderer {
    private static final LoadOp CLEAR_COLOR = LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f);

    private final SpriteBatch batch;
    private final TextureRegion[] regions;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("Platformer");

    /**
     * Creates a renderer over the sample's retained graphics resources.
     *
     * @param batch the sprite batch
     * @param regions the texture regions
     */
    public PlatformerRenderer(SpriteBatch batch, TextureRegion[] regions) {
        this.batch = batch;
        this.regions = regions;
    }

    /**
     * Renders one platformer frame.
     *
     * @param frame the current graphics frame
     * @param colorTarget the destination color target
     * @param width the destination width
     * @param height the destination height
     * @param game the game model
     */
    public void render(
            GraphicsFrame frame,
            TextureView colorTarget,
            int width,
            int height,
            PlatformerGame game) {
        if (batch == null || regions == null || game == null) {
            return;
        }
        int viewportSize = squareViewportSize(width, height);
        int viewportX = centeredViewportOffset(width, viewportSize);
        int viewportY = centeredViewportOffset(height, viewportSize);
        // The fixed level is authored in a square [-1, 1] view. Letterboxing
        // preserves that framing without stretching world units on wide displays.
        batch.viewport(viewportSize, viewportSize);
        RenderPass pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(colorTarget)
                .colorLoadOp(CLEAR_COLOR)
                .colorStoreOp(StoreOp.store()));
        boolean batchBegun = false;
        Throwable renderFailure = null;
        try {
            pass.setViewport(viewportX, viewportY, viewportSize, viewportSize);
            batch.begin(pass);
            batchBegun = true;
            drawLayer(game, PlatformerConstants.LAYER_BACKGROUND);
            drawLayer(game, PlatformerConstants.LAYER_DECORATION);
            drawLayer(game, PlatformerConstants.LAYER_PLATFORM);
            drawLayer(game, PlatformerConstants.LAYER_ITEM);
            drawLayer(game, PlatformerConstants.LAYER_HAZARD);
            drawLayer(game, PlatformerConstants.LAYER_GOAL);
            drawLayer(game, PlatformerConstants.LAYER_ENEMY);
            drawLayer(game, PlatformerConstants.LAYER_PLAYER);
            drawHud(game);
        } catch (RuntimeException | Error failure) {
            renderFailure = failure;
            throw failure;
        } finally {
            finishRender(pass, batchBegun, renderFailure);
        }
    }

    static int squareViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Platformer viewport dimensions must be greater than zero");
        }
        return Math.min(width, height);
    }

    static int centeredViewportOffset(int targetSize, int viewportSize) {
        if (targetSize <= 0 || viewportSize <= 0 || viewportSize > targetSize) {
            throw new IllegalArgumentException("Platformer viewport must fit inside its render target");
        }
        return (targetSize - viewportSize) / 2;
    }

    private void drawLayer(PlatformerGame game, int layer) {
        for (int i = 0; i < game.spriteCount(); i++) {
            PlatformerGame.Sprite sprite = game.spriteAt(i);
            int regionId = sprite.regionId();
            if (sprite.layer() != layer || !validRegion(regionId) || sprite.collected()) {
                continue;
            }
            float screenX = sprite.x() - game.cameraX() * sprite.parallax();
            if (screenX + sprite.halfWidth() < -1.14f || screenX - sprite.halfWidth() > 1.14f) {
                continue;
            }
            batch.color(1.0f, 1.0f, 1.0f, 1.0f);
            batch.draw(regions[regionId], screenX - sprite.halfWidth(),
                    sprite.y() - sprite.halfHeight(), sprite.halfWidth() * 2.0f,
                    sprite.halfHeight() * 2.0f);
        }
        batch.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawHud(PlatformerGame game) {
        if (game.coinsCollected() <= 0 || !validRegion(PlatformerConstants.REGION_COIN)) {
            return;
        }
        int coins = Math.min(game.coinsCollected(), 12);
        TextureRegion coin = regions[PlatformerConstants.REGION_COIN];
        for (int i = 0; i < coins; i++) {
            batch.draw(coin, -0.96f + i * 0.042f, 0.86f, 0.034f, 0.034f);
        }
    }

    private boolean validRegion(int regionId) {
        return regionId >= 0 && regionId < regions.length && regions[regionId] != null;
    }

    private void finishRender(RenderPass pass, boolean batchBegun, Throwable renderFailure) {
        Throwable failure = renderFailure;
        if (batchBegun) {
            try {
                batch.end();
            } catch (RuntimeException | Error batchFailure) {
                failure = aggregateFailure(failure, batchFailure);
            }
        }
        try {
            pass.end();
        } catch (RuntimeException | Error passFailure) {
            failure = aggregateFailure(failure, passFailure);
        }
        if (renderFailure == null) {
            rethrowFailure(failure);
        }
    }

    private static Throwable aggregateFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
