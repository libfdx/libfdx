package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.BoundsComponent;
import io.github.libfdx.samples.ecs.platformer.component.CollectibleComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;
import io.github.libfdx.samples.ecs.platformer.component.RenderSpriteComponent;

public final class RenderSystem implements io.github.libfdx.ecs.system.RenderSystem {
    private static final LoadOp CLEAR_COLOR = LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f);

    private final SpriteBatch batch;
    private final TextureRegion[] regions;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("ECS platformer");
    private boolean enabled = true;
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<PositionComponent> positions;
    private ComponentMapper<BoundsComponent> bounds;
    private ComponentMapper<RenderSpriteComponent> sprites;
    private ComponentMapper<CollectibleComponent> collectibles;
    private EntityList renderables;

    public RenderSystem(SpriteBatch batch, TextureRegion[] regions) {
        this.batch = batch;
        this.regions = regions;
    }

    @Override
    public void onAttach(World world) {
        states = world.mapper(LevelStateComponent.class);
        positions = world.mapper(PositionComponent.class);
        bounds = world.mapper(BoundsComponent.class);
        sprites = world.mapper(RenderSpriteComponent.class);
        collectibles = world.mapper(CollectibleComponent.class);
        renderables = world.entities(world.matcher().all(PositionComponent.class, BoundsComponent.class, RenderSpriteComponent.class));
    }

    @Override
    public void render(
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            Camera camera) {
        if (batch == null || regions == null) {
            return;
        }
        LevelStateComponent state = firstState();
        float cameraX = state != null ? state.cameraX : 0.0f;
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
            drawLayer(PlatformerConstants.LAYER_BACKGROUND, cameraX);
            drawLayer(PlatformerConstants.LAYER_DECORATION, cameraX);
            drawLayer(PlatformerConstants.LAYER_PLATFORM, cameraX);
            drawLayer(PlatformerConstants.LAYER_ITEM, cameraX);
            drawLayer(PlatformerConstants.LAYER_HAZARD, cameraX);
            drawLayer(PlatformerConstants.LAYER_GOAL, cameraX);
            drawLayer(PlatformerConstants.LAYER_ENEMY, cameraX);
            drawLayer(PlatformerConstants.LAYER_PLAYER, cameraX);
            drawHud(state);
        } catch (RuntimeException | Error failure) {
            renderFailure = failure;
            throw failure;
        } finally {
            finishRender(pass, batchBegun, renderFailure);
        }
    }

    @Override
    public void onDetach(World world) {
        states = null;
        positions = null;
        bounds = null;
        sprites = null;
        collectibles = null;
        renderables = null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void drawLayer(int layer, float cameraX) {
        for (int i = 0; i < renderables.size(); i++) {
            int entity = renderables.entityAt(i);
            RenderSpriteComponent sprite = sprites.require(entity);
            if (sprite.layer != layer || sprite.regionId < 0 || sprite.regionId >= regions.length
                    || regions[sprite.regionId] == null) {
                continue;
            }
            if (collectibles.has(entity) && collectibles.require(entity).collected) {
                continue;
            }
            PositionComponent position = positions.require(entity);
            BoundsComponent entityBounds = bounds.require(entity);
            float screenX = screenX(position, sprite, cameraX);
            if (screenX + entityBounds.halfWidth < -1.14f || screenX - entityBounds.halfWidth > 1.14f) {
                continue;
            }
            batch.color(sprite.red, sprite.green, sprite.blue, sprite.alpha);
            batch.draw(regions[sprite.regionId], screenX - entityBounds.halfWidth,
                    position.y - entityBounds.halfHeight, entityBounds.halfWidth * 2.0f,
                    entityBounds.halfHeight * 2.0f);
        }
        batch.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawHud(LevelStateComponent state) {
        if (state == null || state.coinsCollected <= 0 || !validRegion(PlatformerConstants.REGION_COIN)) {
            return;
        }
        int coins = Math.min(state.coinsCollected, 12);
        TextureRegion coin = regions[PlatformerConstants.REGION_COIN];
        for (int i = 0; i < coins; i++) {
            batch.draw(coin, -0.96f + i * 0.042f, 0.86f, 0.034f, 0.034f);
        }
    }

    private boolean validRegion(int regionId) {
        return regionId >= 0 && regionId < regions.length && regions[regionId] != null;
    }

    private float screenX(PositionComponent position, RenderSpriteComponent sprite, float cameraX) {
        return position.x - cameraX * sprite.parallax;
    }

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
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
