package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PlayerComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;

public final class CameraSystem extends BaseGameSystem {
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<PlayerComponent> players;
    private ComponentMapper<PositionComponent> positions;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelStateComponent.class);
        players = world.mapper(PlayerComponent.class);
        positions = world.mapper(PositionComponent.class);
    }

    @Override
    public void update() {
        LevelStateComponent state = firstState();
        if (state == null || players.size() == 0) {
            return;
        }
        int player = players.entityAt(0);
        PositionComponent position = positions.require(player);
        float target = clamp(position.x - 0.25f, PlatformerConstants.CAMERA_MIN_X,
                PlatformerConstants.CAMERA_MAX_X);
        float alpha = Math.min(1.0f, deltaTime() * PlatformerConstants.CAMERA_FOLLOW_SPEED);
        state.cameraX += (target - state.cameraX) * alpha;
    }

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
