package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.component.Enemy;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.Velocity;

public final class EnemySystem extends BaseGameSystem {
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Enemy> enemies;
    private ComponentMapper<Position> positions;
    private ComponentMapper<Velocity> velocities;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelState.class);
        enemies = world.mapper(Enemy.class);
        positions = world.mapper(Position.class);
        velocities = world.mapper(Velocity.class);
    }

    @Override
    public void update() {
        LevelState state = firstState();
        if (state == null || state.gameOver || state.completed || state.restarting) {
            return;
        }
        float delta = deltaTime();
        for (int i = 0; i < enemies.size(); i++) {
            int entity = enemies.entityAt(i);
            Enemy enemy = enemies.componentAt(i);
            Position position = positions.require(entity);
            Velocity velocity = velocities.require(entity);
            velocity.x = enemy.speed * enemy.direction;
            position.x += velocity.x * delta;
            if (position.x < enemy.minX) {
                position.x = enemy.minX;
                enemy.direction = 1;
            } else if (position.x > enemy.maxX) {
                position.x = enemy.maxX;
                enemy.direction = -1;
            }
        }
    }

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }
}
