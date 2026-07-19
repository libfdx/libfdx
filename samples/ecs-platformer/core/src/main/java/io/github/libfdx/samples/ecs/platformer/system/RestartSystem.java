package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.CollectibleComponent;
import io.github.libfdx.samples.ecs.platformer.component.EnemyComponent;
import io.github.libfdx.samples.ecs.platformer.component.InputStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PlayerComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;
import io.github.libfdx.samples.ecs.platformer.component.VelocityComponent;

public final class RestartSystem extends BaseGameSystem {
    private ComponentMapper<InputStateComponent> inputs;
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<PositionComponent> positions;
    private ComponentMapper<VelocityComponent> velocities;
    private ComponentMapper<PlayerComponent> players;
    private ComponentMapper<CollectibleComponent> collectibles;
    private ComponentMapper<EnemyComponent> enemies;

    @Override
    protected void attach(World world) {
        inputs = world.mapper(InputStateComponent.class);
        states = world.mapper(LevelStateComponent.class);
        positions = world.mapper(PositionComponent.class);
        velocities = world.mapper(VelocityComponent.class);
        players = world.mapper(PlayerComponent.class);
        collectibles = world.mapper(CollectibleComponent.class);
        enemies = world.mapper(EnemyComponent.class);
    }

    @Override
    public void update() {
        LevelStateComponent state = firstState();
        InputStateComponent input = firstInput();
        if (state != null && state.restarting) {
            state.restarting = false;
            return;
        }
        if (state == null || input == null || (!state.gameOver && !state.completed) || !input.restartPressed) {
            return;
        }
        state.reset();
        state.restarting = true;
        for (int i = 0; i < players.size(); i++) {
            int entity = players.entityAt(i);
            PositionComponent position = positions.require(entity);
            VelocityComponent velocity = velocities.require(entity);
            PlayerComponent player = players.componentAt(i);
            position.x = PlatformerConstants.PLAYER_START_X;
            position.y = PlatformerConstants.PLAYER_START_Y;
            velocity.x = 0.0f;
            velocity.y = 0.0f;
            player.onGround = true;
            player.facingRight = true;
        }
        for (int i = 0; i < collectibles.size(); i++) {
            collectibles.componentAt(i).collected = false;
        }
        for (int i = 0; i < enemies.size(); i++) {
            int entity = enemies.entityAt(i);
            enemies.componentAt(i).reset(positions.require(entity), velocities.require(entity));
        }
    }

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private InputStateComponent firstInput() {
        return inputs.size() > 0 ? inputs.componentAt(0) : null;
    }
}
