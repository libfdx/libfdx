package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.Collectible;
import io.github.libfdx.samples.ecs.platformer.component.Enemy;
import io.github.libfdx.samples.ecs.platformer.component.InputState;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.Velocity;

public final class RestartSystem extends BaseGameSystem {
    private ComponentMapper<InputState> inputs;
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Position> positions;
    private ComponentMapper<Velocity> velocities;
    private ComponentMapper<Player> players;
    private ComponentMapper<Collectible> collectibles;
    private ComponentMapper<Enemy> enemies;

    @Override
    protected void attach(World world) {
        inputs = world.mapper(InputState.class);
        states = world.mapper(LevelState.class);
        positions = world.mapper(Position.class);
        velocities = world.mapper(Velocity.class);
        players = world.mapper(Player.class);
        collectibles = world.mapper(Collectible.class);
        enemies = world.mapper(Enemy.class);
    }

    @Override
    public void update() {
        LevelState state = firstState();
        InputState input = firstInput();
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
            Position position = positions.require(entity);
            Velocity velocity = velocities.require(entity);
            Player player = players.componentAt(i);
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

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private InputState firstInput() {
        return inputs.size() > 0 ? inputs.componentAt(0) : null;
    }
}
