package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.component.Bounds;
import io.github.libfdx.samples.ecs.platformer.component.Collectible;
import io.github.libfdx.samples.ecs.platformer.component.Enemy;
import io.github.libfdx.samples.ecs.platformer.component.Goal;
import io.github.libfdx.samples.ecs.platformer.component.Hazard;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;

public final class InteractionSystem extends BaseGameSystem {
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Position> positions;
    private ComponentMapper<Bounds> bounds;
    private ComponentMapper<Player> players;
    private ComponentMapper<Collectible> collectibles;
    private EntityList collectibleEntities;
    private EntityList hazards;
    private EntityList enemies;
    private EntityList goals;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelState.class);
        positions = world.mapper(Position.class);
        bounds = world.mapper(Bounds.class);
        players = world.mapper(Player.class);
        collectibles = world.mapper(Collectible.class);
        collectibleEntities = world.entities(world.matcher().all(Collectible.class, Position.class, Bounds.class));
        hazards = world.entities(world.matcher().all(Hazard.class, Position.class, Bounds.class));
        enemies = world.entities(world.matcher().all(Enemy.class, Position.class, Bounds.class));
        goals = world.entities(world.matcher().all(Goal.class, Position.class, Bounds.class));
    }

    @Override
    public void update() {
        LevelState state = firstState();
        if (state == null || state.gameOver || state.completed || state.restarting) {
            return;
        }
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            int player = players.entityAt(playerIndex);
            Position playerPosition = positions.require(player);
            Bounds playerBounds = bounds.require(player);
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

    private void collectItems(LevelState state, Position playerPosition, Bounds playerBounds) {
        for (int i = 0; i < collectibleEntities.size(); i++) {
            int entity = collectibleEntities.entityAt(i);
            Collectible collectible = collectibles.require(entity);
            if (collectible.collected) {
                continue;
            }
            if (overlaps(playerPosition, playerBounds, positions.require(entity), bounds.require(entity))) {
                collectible.collected = true;
                state.coinsCollected += collectible.value;
            }
        }
    }

    private boolean touchesAny(Position playerPosition, Bounds playerBounds, EntityList targets) {
        for (int i = 0; i < targets.size(); i++) {
            int entity = targets.entityAt(i);
            if (overlaps(playerPosition, playerBounds, positions.require(entity), bounds.require(entity))) {
                return true;
            }
        }
        return false;
    }

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private static boolean overlaps(Position aPosition, Bounds aBounds, Position bPosition, Bounds bBounds) {
        return Math.abs(aPosition.x - bPosition.x) < aBounds.halfWidth + bBounds.halfWidth
                && Math.abs(aPosition.y - bPosition.y) < aBounds.halfHeight + bBounds.halfHeight;
    }
}
