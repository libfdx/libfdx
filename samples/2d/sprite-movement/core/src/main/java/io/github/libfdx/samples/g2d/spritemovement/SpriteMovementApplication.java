package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.samples.g2d.spritemovement.input.KeyboardMovementInput;
import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;
import io.github.libfdx.samples.g2d.spritemovement.render.SpriteMovementTextures;

/**
 * Portable 2D Sprite Movement application.
 *
 * <p>The application owns its game state, camera, batch, and textures. Platform
 * launchers only select a backend and graphics provider before starting it.</p>
 */
public final class SpriteMovementApplication extends ApplicationAdapter {
    public static final String PLAYER_SPRITE = "sprites/player.png";
    public static final String WALL_TILE = "tiles/wall.png";

    private static final float MIN_VIEWPORT_SIZE = 0.01f;
    private static final float CAMERA_VIEWPORT_HEIGHT = 6.0f;
    private static final float WALL_TILE_SIZE = 0.5f;
    private static final int INITIAL_SPRITE_CAPACITY = 64;
    private static final LoadOp CLEAR_COLOR = LoadOp.clear(0.08f, 0.10f, 0.14f, 1.0f);
    private static final float PLAYER_RED = 0.20392157f;
    private static final float PLAYER_GREEN = 0.8392157f;
    private static final float PLAYER_BLUE = 0.8235294f;
    private static final float WALL_RED = 0.5176471f;
    private static final float WALL_GREEN = 0.5568628f;
    private static final float WALL_BLUE = 0.61960787f;

    private final long exitAfterFrames;
    private final SpriteMovementState state = new SpriteMovementState();
    private final Camera camera = new Camera();
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("sprite movement scene");
    private Application application;
    private GraphicsContext graphics;
    private MovementInput input;
    private SpriteBatch batch;
    private SpriteMovementTextures textures;

    /** Creates an application that runs until the platform requests an exit. */
    public SpriteMovementApplication() {
        this(0L);
    }

    /**
     * Creates an application with an optional bounded lifetime for smoke tests.
     *
     * @param exitAfterFrames frames to render before exiting, or zero to keep running
     */
    public SpriteMovementApplication(long exitAfterFrames) {
        this.exitAfterFrames = Math.max(0L, exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        if (fdx == null) {
            throw new IllegalArgumentException("fdx cannot be null.");
        }
        GraphicsContext nextGraphics = fdx.graphics().main();
        MovementInput nextInput = new KeyboardMovementInput(fdx.input());
        SpriteBatch nextBatch = new SpriteBatch(nextGraphics, INITIAL_SPRITE_CAPACITY);
        SpriteMovementTextures nextTextures;
        try {
            nextTextures = new SpriteMovementTextures(fdx.files(), nextGraphics);
        } catch (RuntimeException | Error failure) {
            try {
                nextBatch.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        application = fdx.app();
        graphics = nextGraphics;
        input = nextInput;
        batch = nextBatch;
        textures = nextTextures;
        state.reset();
    }

    @Override
    public void render() {
        if (application == null || graphics == null || batch == null || textures == null) {
            return;
        }
        state.update(input, application.deltaTime());
        GraphicsFrame frame = graphics.currentFrame();
        TextureView colorTarget = frame != null ? frame.colorAttachment() : null;
        if (colorTarget == null) {
            throw new IllegalStateException("The main graphics context has no current render frame.");
        }
        render(frame, colorTarget, frame.width(), frame.height());
        if (exitAfterFrames > 0L && application.frameId() >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        Throwable failure = null;
        if (textures != null) {
            try {
                textures.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                failure = disposeFailure;
            }
            textures = null;
        }
        if (batch != null) {
            try {
                batch.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                failure = aggregateFailure(failure, disposeFailure);
            }
            batch = null;
        }
        input = null;
        graphics = null;
        application = null;
        rethrowFailure(failure);
    }

    private void render(GraphicsFrame frame, TextureView colorTarget, int width, int height) {
        updateCamera(width, height);
        float cameraViewportWidth = camera.viewportWidth() * camera.zoom();
        float cameraViewportHeight = camera.viewportHeight() * camera.zoom();
        float cameraX = camera.position().x();
        float cameraY = camera.position().y();

        batch.viewport(width, height);
        RenderPass pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(colorTarget)
                .colorLoadOp(CLEAR_COLOR)
                .colorStoreOp(StoreOp.store()));
        boolean batchBegun = false;
        Throwable renderFailure = null;
        try {
            batch.begin(pass);
            batchBegun = true;

            batch.color(PLAYER_RED, PLAYER_GREEN, PLAYER_BLUE, 1.0f);
            drawSprite(
                    textures.player(),
                    state.playerX(),
                    state.playerY(),
                    SpriteMovementState.PLAYER_WIDTH,
                    SpriteMovementState.PLAYER_HEIGHT,
                    cameraX,
                    cameraY,
                    cameraViewportWidth,
                    cameraViewportHeight);

            batch.color(WALL_RED, WALL_GREEN, WALL_BLUE, 1.0f);
            Texture wallTexture = textures.wall();
            for (int i = 0; i < state.wallCount(); i++) {
                drawTiledWall(
                        wallTexture,
                        state.wallAt(i),
                        cameraX,
                        cameraY,
                        cameraViewportWidth,
                        cameraViewportHeight);
            }
        } catch (RuntimeException | Error failure) {
            renderFailure = failure;
            throw failure;
        } finally {
            finishRender(batch, pass, batchBegun, renderFailure);
        }
    }

    private void updateCamera(int width, int height) {
        float aspect = height > 0 ? Math.max(1, width) / (float) height : 1.0f;
        camera.projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(CAMERA_VIEWPORT_HEIGHT * aspect, CAMERA_VIEWPORT_HEIGHT)
                .position(0.0f, 0.0f, 10.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .nearFar(0.1f, 100.0f)
                .update();
    }

    private void drawTiledWall(
            Texture texture,
            SpriteMovementState.Wall wall,
            float cameraX,
            float cameraY,
            float cameraViewportWidth,
            float cameraViewportHeight) {
        float width = Math.abs(wall.width);
        float height = Math.abs(wall.height);
        int columns = wallTileCount(width);
        int rows = wallTileCount(height);
        float tileWidth = width / columns;
        float tileHeight = height / rows;
        float firstTileX = wall.x - width * 0.5f + tileWidth * 0.5f;
        float firstTileY = wall.y - height * 0.5f + tileHeight * 0.5f;
        for (int row = 0; row < rows; row++) {
            float worldY = firstTileY + row * tileHeight;
            for (int column = 0; column < columns; column++) {
                float worldX = firstTileX + column * tileWidth;
                drawSprite(
                        texture,
                        worldX,
                        worldY,
                        tileWidth,
                        tileHeight,
                        cameraX,
                        cameraY,
                        cameraViewportWidth,
                        cameraViewportHeight);
            }
        }
    }

    private void drawSprite(
            Texture texture,
            float worldX,
            float worldY,
            float worldWidth,
            float worldHeight,
            float cameraX,
            float cameraY,
            float cameraViewportWidth,
            float cameraViewportHeight) {
        float drawWidth = toClipSize(worldWidth, cameraViewportWidth);
        float drawHeight = toClipSize(worldHeight, cameraViewportHeight);
        float x = toClipCenter(worldX, cameraX, cameraViewportWidth) - drawWidth * 0.5f;
        float y = toClipCenter(worldY, cameraY, cameraViewportHeight) - drawHeight * 0.5f;
        batch.draw(texture, x, y, drawWidth, drawHeight);
    }

    public static int wallTileCount(float worldSize) {
        return Math.max(1, (int) Math.ceil(Math.abs(worldSize) / WALL_TILE_SIZE));
    }

    public static float toClipSize(float worldSize, float viewportSize) {
        return Math.abs(worldSize) * 2.0f / Math.max(MIN_VIEWPORT_SIZE, viewportSize);
    }

    public static float toClipCenter(float worldPosition, float cameraPosition, float viewportSize) {
        return (worldPosition - cameraPosition) * 2.0f / Math.max(MIN_VIEWPORT_SIZE, viewportSize);
    }

    static void finishRender(Batch2D batch, RenderPass pass, boolean batchBegun, Throwable renderFailure) {
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
