package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;

public final class CameraSystem extends BaseGameSystem {
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Player> players;
    private ComponentMapper<Position> positions;

    @Override
    protected void attach(World world) {
        states = world.mapper(LevelState.class);
        players = world.mapper(Player.class);
        positions = world.mapper(Position.class);
    }

    @Override
    public void update() {
        LevelState state = firstState();
        if (state == null || players.size() == 0) {
            return;
        }
        int player = players.entityAt(0);
        Position position = positions.require(player);
        float target = clamp(position.x - 0.25f, PlatformerConstants.CAMERA_MIN_X,
                PlatformerConstants.CAMERA_MAX_X);
        float alpha = Math.min(1.0f, deltaTime() * PlatformerConstants.CAMERA_FOLLOW_SPEED);
        state.cameraX += (target - state.cameraX) * alpha;
    }

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
