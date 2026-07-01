package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.Bounds;
import io.github.libfdx.samples.ecs.platformer.component.InputState;
import io.github.libfdx.samples.ecs.platformer.component.LevelState;
import io.github.libfdx.samples.ecs.platformer.component.Player;
import io.github.libfdx.samples.ecs.platformer.component.Position;
import io.github.libfdx.samples.ecs.platformer.component.RenderSprite;
import io.github.libfdx.samples.ecs.platformer.component.Solid;
import io.github.libfdx.samples.ecs.platformer.component.Velocity;

public final class PlayerSystem extends BaseGameSystem {
    private ComponentMapper<InputState> inputs;
    private ComponentMapper<LevelState> states;
    private ComponentMapper<Position> positions;
    private ComponentMapper<Velocity> velocities;
    private ComponentMapper<Bounds> bounds;
    private ComponentMapper<Player> players;
    private ComponentMapper<RenderSprite> sprites;
    private EntityList solids;

    @Override
    protected void attach(World world) {
        inputs = world.mapper(InputState.class);
        states = world.mapper(LevelState.class);
        positions = world.mapper(Position.class);
        velocities = world.mapper(Velocity.class);
        bounds = world.mapper(Bounds.class);
        players = world.mapper(Player.class);
        sprites = world.mapper(RenderSprite.class);
        solids = world.entities(world.matcher().all(Solid.class, Position.class, Bounds.class));
    }

    @Override
    public void update() {
        LevelState state = firstState();
        InputState input = firstInput();
        if (state == null || input == null || state.restarting) {
            return;
        }
        float delta = deltaTime();
        for (int i = 0; i < players.size(); i++) {
            int entity = players.entityAt(i);
            Position position = positions.require(entity);
            Velocity velocity = velocities.require(entity);
            Bounds playerBounds = bounds.require(entity);
            Player player = players.componentAt(i);
            if (state.gameOver || state.completed) {
                velocity.x = 0.0f;
                return;
            }
            float horizontal = 0.0f;
            if (input.leftDown) {
                horizontal -= 1.0f;
            }
            if (input.rightDown) {
                horizontal += 1.0f;
            }
            velocity.x = horizontal * PlatformerConstants.PLAYER_MOVE_SPEED;
            if (horizontal < 0.0f) {
                player.facingRight = false;
            } else if (horizontal > 0.0f) {
                player.facingRight = true;
            }
            if (input.jumpPressed && player.onGround) {
                velocity.y = PlatformerConstants.JUMP_VELOCITY;
                player.onGround = false;
            }
            velocity.y += PlatformerConstants.GRAVITY * delta;
            if (velocity.y < PlatformerConstants.TERMINAL_VELOCITY) {
                velocity.y = PlatformerConstants.TERMINAL_VELOCITY;
            }
            position.x += velocity.x * delta;
            resolveHorizontal(position, playerBounds, velocity);
            position.x = clamp(position.x, PlatformerConstants.LEVEL_LEFT + playerBounds.halfWidth,
                    PlatformerConstants.LEVEL_RIGHT - playerBounds.halfWidth);

            position.y += velocity.y * delta;
            player.onGround = false;
            resolveVertical(position, playerBounds, velocity, player);
            if (position.y < PlatformerConstants.FALL_Y) {
                state.gameOver = true;
                velocity.y = 0.0f;
            }
            RenderSprite sprite = sprites.get(entity);
            if (sprite != null) {
                sprite.regionId = player.onGround && Math.abs(velocity.x) > 0.001f
                        ? PlatformerConstants.REGION_PLAYER_WALK
                        : PlatformerConstants.REGION_PLAYER_IDLE;
            }
        }
    }

    private void resolveHorizontal(Position position, Bounds playerBounds, Velocity velocity) {
        if (velocity.x == 0.0f) {
            return;
        }
        for (int i = 0; i < solids.size(); i++) {
            int solid = solids.entityAt(i);
            Position solidPosition = positions.require(solid);
            Bounds solidBounds = bounds.require(solid);
            if (!overlaps(position, playerBounds, solidPosition, solidBounds)) {
                continue;
            }
            if (velocity.x > 0.0f) {
                position.x = solidPosition.x - solidBounds.halfWidth - playerBounds.halfWidth;
            } else {
                position.x = solidPosition.x + solidBounds.halfWidth + playerBounds.halfWidth;
            }
            velocity.x = 0.0f;
        }
    }

    private void resolveVertical(Position position, Bounds playerBounds, Velocity velocity, Player player) {
        for (int i = 0; i < solids.size(); i++) {
            int solid = solids.entityAt(i);
            Position solidPosition = positions.require(solid);
            Bounds solidBounds = bounds.require(solid);
            if (!overlaps(position, playerBounds, solidPosition, solidBounds)) {
                continue;
            }
            if (velocity.y <= 0.0f) {
                position.y = solidPosition.y + solidBounds.halfHeight + playerBounds.halfHeight;
                player.onGround = true;
            } else {
                position.y = solidPosition.y - solidBounds.halfHeight - playerBounds.halfHeight;
            }
            velocity.y = 0.0f;
        }
    }

    private static boolean overlaps(Position aPosition, Bounds aBounds, Position bPosition, Bounds bBounds) {
        return Math.abs(aPosition.x - bPosition.x) < aBounds.halfWidth + bBounds.halfWidth
                && Math.abs(aPosition.y - bPosition.y) < aBounds.halfHeight + bBounds.halfHeight;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private LevelState firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private InputState firstInput() {
        return inputs.size() > 0 ? inputs.componentAt(0) : null;
    }
}
