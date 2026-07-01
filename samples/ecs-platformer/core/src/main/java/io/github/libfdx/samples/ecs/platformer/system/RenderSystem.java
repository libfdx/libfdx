package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.Bounds;
import io.github.libfdx.samples.ecs.platformer.component.Collectible;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.RenderSprite;

public final class RenderSystem extends BaseGameSystem {
    private final SpriteBatch batch;
    private final TextureRegion[] regions;
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Position> positions;
    private ComponentMapper<Bounds> bounds;
    private ComponentMapper<RenderSprite> sprites;
    private ComponentMapper<Collectible> collectibles;
    private EntityList renderables;

    public RenderSystem(SpriteBatch batch, TextureRegion[] regions) {
        this.batch = batch;
        this.regions = regions;
    }

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelState.class);
        positions = world.mapper(Position.class);
        bounds = world.mapper(Bounds.class);
        sprites = world.mapper(RenderSprite.class);
        collectibles = world.mapper(Collectible.class);
        renderables = world.entities(world.matcher().all(Position.class, Bounds.class, RenderSprite.class));
    }

    @Override
    public void update() {
        if (batch == null || regions == null) {
            return;
        }
        LevelState state = firstState();
        float cameraX = state != null ? state.cameraX : 0.0f;
        batch.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
        drawLayer(PlatformerConstants.LAYER_BACKGROUND, cameraX);
        drawLayer(PlatformerConstants.LAYER_DECORATION, cameraX);
        drawLayer(PlatformerConstants.LAYER_PLATFORM, cameraX);
        drawLayer(PlatformerConstants.LAYER_ITEM, cameraX);
        drawLayer(PlatformerConstants.LAYER_HAZARD, cameraX);
        drawLayer(PlatformerConstants.LAYER_GOAL, cameraX);
        drawLayer(PlatformerConstants.LAYER_ENEMY, cameraX);
        drawLayer(PlatformerConstants.LAYER_PLAYER, cameraX);
        drawHud(state);
        batch.end();
    }

    private void drawLayer(int layer, float cameraX) {
        for (int i = 0; i < renderables.size(); i++) {
            int entity = renderables.entityAt(i);
            RenderSprite sprite = sprites.require(entity);
            if (sprite.layer != layer || sprite.regionId < 0 || sprite.regionId >= regions.length
                    || regions[sprite.regionId] == null) {
                continue;
            }
            if (collectibles.has(entity) && collectibles.require(entity).collected) {
                continue;
            }
            Position position = positions.require(entity);
            Bounds entityBounds = bounds.require(entity);
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

    private void drawHud(LevelState state) {
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

    private float screenX(Position position, RenderSprite sprite, float cameraX) {
        return position.x - cameraX * sprite.parallax;
    }

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }
}
