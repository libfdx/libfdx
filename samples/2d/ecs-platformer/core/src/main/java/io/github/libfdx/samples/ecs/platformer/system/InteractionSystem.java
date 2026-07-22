package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.component.BoundsComponent;
import io.github.libfdx.samples.ecs.platformer.component.CollectibleComponent;
import io.github.libfdx.samples.ecs.platformer.component.EnemyComponent;
import io.github.libfdx.samples.ecs.platformer.component.GoalComponent;
import io.github.libfdx.samples.ecs.platformer.component.HazardComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PlayerComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;

public final class InteractionSystem extends BaseGameSystem {
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<PositionComponent> positions;
    private ComponentMapper<BoundsComponent> bounds;
    private ComponentMapper<PlayerComponent> players;
    private ComponentMapper<CollectibleComponent> collectibles;
    private EntityList collectibleEntities;
    private EntityList hazards;
    private EntityList enemies;
    private EntityList goals;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelStateComponent.class);
        positions = world.mapper(PositionComponent.class);
        bounds = world.mapper(BoundsComponent.class);
        players = world.mapper(PlayerComponent.class);
        collectibles = world.mapper(CollectibleComponent.class);
        collectibleEntities = world.entities(world.matcher().all(CollectibleComponent.class, PositionComponent.class, BoundsComponent.class));
        hazards = world.entities(world.matcher().all(HazardComponent.class, PositionComponent.class, BoundsComponent.class));
        enemies = world.entities(world.matcher().all(EnemyComponent.class, PositionComponent.class, BoundsComponent.class));
        goals = world.entities(world.matcher().all(GoalComponent.class, PositionComponent.class, BoundsComponent.class));
    }

    @Override
    public void update() {
        LevelStateComponent state = firstState();
        if (state == null || state.gameOver || state.completed || state.restarting) {
            return;
        }
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            int player = players.entityAt(playerIndex);
            PositionComponent playerPosition = positions.require(player);
            BoundsComponent playerBounds = bounds.require(player);
            collectItems(state, playerPosition, playerBounds);
            if (touchesAny(playerPosition, playerBounds, hazards) || touchesAny(playerPosition, playerBounds, enemies)) {
                state.gameOver = true;
                return;
            }
            if (touchesAny(playerPosition, playerBounds, goals)) {
                state.completed = true;
                return;
            }
        }
    }

    private void collectItems(LevelStateComponent state, PositionComponent playerPosition, BoundsComponent playerBounds) {
        for (int i = 0; i < collectibleEntities.size(); i++) {
            int entity = collectibleEntities.entityAt(i);
            CollectibleComponent collectible = collectibles.require(entity);
            if (collectible.collected) {
                continue;
            }
            if (overlaps(playerPosition, playerBounds, positions.require(entity), bounds.require(entity))) {
                collectible.collected = true;
                state.coinsCollected += collectible.value;
            }
        }
    }

    private boolean touchesAny(PositionComponent playerPosition, BoundsComponent playerBounds, EntityList targets) {
        for (int i = 0; i < targets.size(); i++) {
            int entity = targets.entityAt(i);
            if (overlaps(playerPosition, playerBounds, positions.require(entity), bounds.require(entity))) {
                return true;
            }
        }
        return false;
    }

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private static boolean overlaps(PositionComponent aPosition, BoundsComponent aBounds, PositionComponent bPosition, BoundsComponent bBounds) {
        return Math.abs(aPosition.x - bPosition.x) < aBounds.halfWidth + bBounds.halfWidth
                && Math.abs(aPosition.y - bPosition.y) < aBounds.halfHeight + bBounds.halfHeight;
    }
}
