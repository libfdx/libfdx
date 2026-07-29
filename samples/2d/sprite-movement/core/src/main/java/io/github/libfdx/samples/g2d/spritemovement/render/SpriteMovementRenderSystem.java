package io.github.libfdx.samples.g2d.spritemovement.render;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.ecs.manager.CameraManager;
import io.github.libfdx.ecs.system.RenderSystem;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;
import io.github.libfdx.samples.g2d.spritemovement.component.Camera2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.Sprite2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.WallComponent;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementScenes;

/** World-attached render system and owner of Sprite Movement graphics resources. */
public final class SpriteMovementRenderSystem implements RenderSystem {
    private static final float RADIANS_TO_DEGREES = 57.29577951308232f;
    private static final float MIN_VIEWPORT_SIZE = 0.01f;
    private static final float WALL_TILE_SIZE = 0.5f;
    private static final int INITIAL_SPRITE_CAPACITY = 64;
    private static final LoadOp CLEAR_COLOR = LoadOp.clear(0.08f, 0.10f, 0.14f, 1.0f);

    private final Fdx fdx;
    private final CameraManager cameras;
    private final long exitAfterFrames;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("sprite movement scene");
    private World world;
    private Application application;
    private SpriteBatch batch;
    private SpriteMovementTextures textures;
    private EntityList sprites;
    private EntityList cameraEntities;
    private ComponentMapper<TransformComponent> transforms;
    private ComponentMapper<Sprite2DComponent> spriteComponents;
    private ComponentMapper<WallComponent> walls;
    private int width = 1;
    private int height = 1;
    private int cameraEntity;
    private boolean enabled = true;

    public SpriteMovementRenderSystem(Fdx fdx, CameraManager cameras, long exitAfterFrames) {
        if (fdx == null || cameras == null) {
            throw new IllegalArgumentException("fdx and cameras cannot be null.");
        }
        this.fdx = fdx;
        this.cameras = cameras;
        this.exitAfterFrames = Math.max(0L, exitAfterFrames);
    }

    @Override
    public void onAttach(World world) {
        this.world = world;
        application = fdx.app();
        batch = new SpriteBatch(fdx.graphics().main(), INITIAL_SPRITE_CAPACITY);
        textures = new SpriteMovementTextures(fdx, fdx.graphics().main());
        transforms = world.mapper(TransformComponent.class);
        spriteComponents = world.mapper(Sprite2DComponent.class);
        walls = world.mapper(WallComponent.class);
        sprites = world.entities(world.matcher().all(TransformComponent.class, Sprite2DComponent.class));
        cameraEntities = world.entities(world.matcher().all(Camera2DComponent.class));
        updateCamera();
    }

    @Override
    public void render(
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            Camera camera) {
        Camera previousManagedCamera = cameras.game();
        this.width = width;
        this.height = height;
        updateCamera();
        Camera renderCamera = camera == previousManagedCamera ? cameras.game() : camera;
        float fallbackViewportHeight = cameraEntity != 0
                ? world.require(cameraEntity, Camera2DComponent.class).viewportHeight
                : 6.0f;
        fallbackViewportHeight = Math.max(MIN_VIEWPORT_SIZE, fallbackViewportHeight);
        float fallbackViewportWidth = fallbackViewportHeight * width / (float) Math.max(1, height);
        float cameraViewportWidth = renderCamera != null
                ? renderCamera.viewportWidth() * renderCamera.zoom()
                : fallbackViewportWidth;
        float cameraViewportHeight = renderCamera != null
                ? renderCamera.viewportHeight() * renderCamera.zoom()
                : fallbackViewportHeight;
        float cameraX = renderCamera != null ? renderCamera.position().x() : 0.0f;
        float cameraY = renderCamera != null ? renderCamera.position().y() : 0.0f;

        // SpriteBatch consumes clip-space bounds; its viewport only preserves pixel-correct rotation.
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
            for (int i = 0; i < sprites.size(); i++) {
                int entity = sprites.entityAt(i);
                Transform transform = transforms.require(entity).transform;
                Sprite2DComponent sprite = spriteComponents.require(entity);
                Texture texture = textures.texture(sprite.assetPath);
                if (texture == null) {
                    continue;
                }
                float rotation = rotationDegrees(transform);
                batch.color(sprite.red, sprite.green, sprite.blue, sprite.alpha);
                if (walls.has(entity)) {
                    drawTiledWall(texture, transform, sprite, cameraX, cameraY,
                            cameraViewportWidth, cameraViewportHeight, rotation);
                } else {
                    drawSprite(texture, transform.x(), transform.y(),
                            sprite.width * transform.scaleX(), sprite.height * transform.scaleY(),
                            cameraX, cameraY, cameraViewportWidth, cameraViewportHeight, rotation);
                }
            }
        } catch (RuntimeException | Error failure) {
            renderFailure = failure;
            throw failure;
        } finally {
            finishRender(batch, pass, batchBegun, renderFailure);
        }

        if (exitAfterFrames > 0L
                && application.frameId() >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void onDetach(World world) {
        cameras.game(null);
        sprites = null;
        cameraEntities = null;
        transforms = null;
        spriteComponents = null;
        walls = null;
        if (textures != null) {
            textures.dispose();
            textures = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        application = null;
        this.world = null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void refreshCamera(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        updateCamera();
    }

    private void updateCamera() {
        cameraEntity = 0;
        for (int i = 0; i < cameraEntities.size(); i++) {
            int entity = cameraEntities.entityAt(i);
            Camera2DComponent camera = world.require(entity, Camera2DComponent.class);
            if (cameraEntity == 0) {
                cameraEntity = entity;
            }
            if (camera.primary) {
                cameraEntity = entity;
                break;
            }
        }
        cameras.game(cameraEntity != 0
                ? SpriteMovementScenes.updateCamera(world, cameraEntity, width, height)
                : null);
    }

    private void drawTiledWall(Texture texture, Transform transform, Sprite2DComponent sprite,
            float cameraX, float cameraY, float cameraViewportWidth, float cameraViewportHeight,
            float rotation) {
        float width = Math.abs(sprite.width);
        float height = Math.abs(sprite.height);
        int columns = wallTileCount(width * transform.scaleX());
        int rows = wallTileCount(height * transform.scaleY());
        float tileWidth = width / columns;
        float tileHeight = height / rows;
        float firstTileX = -width * 0.5f + tileWidth * 0.5f;
        float firstTileY = -height * 0.5f + tileHeight * 0.5f;
        float radians = (float) Math.toRadians(rotation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        for (int row = 0; row < rows; row++) {
            float localY = (firstTileY + row * tileHeight) * transform.scaleY();
            for (int column = 0; column < columns; column++) {
                float localX = (firstTileX + column * tileWidth) * transform.scaleX();
                float worldX = transform.x() + localX * cos - localY * sin;
                float worldY = transform.y() + localX * sin + localY * cos;
                drawSprite(texture, worldX, worldY,
                        tileWidth * transform.scaleX(), tileHeight * transform.scaleY(),
                        cameraX, cameraY, cameraViewportWidth, cameraViewportHeight, rotation);
            }
        }
    }

    private void drawSprite(Texture texture, float worldX, float worldY, float worldWidth, float worldHeight,
            float cameraX, float cameraY, float cameraViewportWidth, float cameraViewportHeight,
            float rotation) {
        float drawWidth = toClipSize(worldWidth, cameraViewportWidth);
        float drawHeight = toClipSize(worldHeight, cameraViewportHeight);
        float x = toClipCenter(worldX, cameraX, cameraViewportWidth) - drawWidth * 0.5f;
        float y = toClipCenter(worldY, cameraY, cameraViewportHeight) - drawHeight * 0.5f;
        batch.draw(texture, x, y, drawWidth, drawHeight,
                drawWidth * 0.5f, drawHeight * 0.5f, rotation);
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

    private static float rotationDegrees(Transform transform) {
        float x = transform.rotation().x();
        float y = transform.rotation().y();
        float z = transform.rotation().z();
        float w = transform.rotation().w();
        float sin = 2.0f * (w * z + x * y);
        float cos = 1.0f - 2.0f * (y * y + z * z);
        return (float) Math.atan2(sin, cos) * RADIANS_TO_DEGREES;
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
