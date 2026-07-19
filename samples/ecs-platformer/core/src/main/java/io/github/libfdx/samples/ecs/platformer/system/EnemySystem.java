package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.component.EnemyComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;
import io.github.libfdx.samples.ecs.platformer.component.VelocityComponent;

public final class EnemySystem extends BaseGameSystem {
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<EnemyComponent> enemies;
    private ComponentMapper<PositionComponent> positions;
    private ComponentMapper<VelocityComponent> velocities;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelStateComponent.class);
        enemies = world.mapper(EnemyComponent.class);
        positions = world.mapper(PositionComponent.class);
        velocities = world.mapper(VelocityComponent.class);
    }

    @Override
    public void update() {
        LevelStateComponent state = firstState();
        if (state == null || state.gameOver || state.completed || state.restarting) {
            return;
        }
        float delta = deltaTime();
        for (int i = 0; i < enemies.size(); i++) {
            int entity = enemies.entityAt(i);
            EnemyComponent enemy = enemies.componentAt(i);
            PositionComponent position = positions.require(entity);
            VelocityComponent velocity = velocities.require(entity);
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

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }
}
