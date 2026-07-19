package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.samples.ecs.platformer.component.BoundsComponent;
import io.github.libfdx.samples.ecs.platformer.component.InputStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.LevelStateComponent;
import io.github.libfdx.samples.ecs.platformer.component.PlayerComponent;
import io.github.libfdx.samples.ecs.platformer.component.PositionComponent;
import io.github.libfdx.samples.ecs.platformer.component.RenderSpriteComponent;
import io.github.libfdx.samples.ecs.platformer.component.SolidComponent;
import io.github.libfdx.samples.ecs.platformer.component.VelocityComponent;

public final class PlayerSystem extends BaseGameSystem {
    private ComponentMapper<InputStateComponent> inputs;
    private ComponentMapper<LevelStateComponent> states;
    private ComponentMapper<PositionComponent> positions;
    private ComponentMapper<VelocityComponent> velocities;
    private ComponentMapper<BoundsComponent> bounds;
    private ComponentMapper<PlayerComponent> players;
    private ComponentMapper<RenderSpriteComponent> sprites;
    private EntityList solids;

    @Override
    protected void attach(World world) {
        inputs = world.mapper(InputStateComponent.class);
        states = world.mapper(LevelStateComponent.class);
        positions = world.mapper(PositionComponent.class);
        velocities = world.mapper(VelocityComponent.class);
        bounds = world.mapper(BoundsComponent.class);
        players = world.mapper(PlayerComponent.class);
        sprites = world.mapper(RenderSpriteComponent.class);
        solids = world.entities(world.matcher().all(SolidComponent.class, PositionComponent.class, BoundsComponent.class));
    }

    @Override
    public void update() {
        LevelStateComponent state = firstState();
        InputStateComponent input = firstInput();
        if (state == null || input == null || state.restarting) {
            return;
        }
        float delta = deltaTime();
        for (int i = 0; i < players.size(); i++) {
            int entity = players.entityAt(i);
            PositionComponent position = positions.require(entity);
            VelocityComponent velocity = velocities.require(entity);
            BoundsComponent playerBounds = bounds.require(entity);
            PlayerComponent player = players.componentAt(i);
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
            RenderSpriteComponent sprite = sprites.get(entity);
            if (sprite != null) {
                sprite.regionId = player.onGround && Math.abs(velocity.x) > 0.001f
                        ? PlatformerConstants.REGION_PLAYER_WALK
                        : PlatformerConstants.REGION_PLAYER_IDLE;
            }
        }
    }

    private void resolveHorizontal(PositionComponent position, BoundsComponent playerBounds, VelocityComponent velocity) {
        if (velocity.x == 0.0f) {
            return;
        }
        for (int i = 0; i < solids.size(); i++) {
            int solid = solids.entityAt(i);
            PositionComponent solidPosition = positions.require(solid);
            BoundsComponent solidBounds = bounds.require(solid);
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

    private void resolveVertical(PositionComponent position, BoundsComponent playerBounds, VelocityComponent velocity, PlayerComponent player) {
        for (int i = 0; i < solids.size(); i++) {
            int solid = solids.entityAt(i);
            PositionComponent solidPosition = positions.require(solid);
            BoundsComponent solidBounds = bounds.require(solid);
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

    private static boolean overlaps(PositionComponent aPosition, BoundsComponent aBounds, PositionComponent bPosition, BoundsComponent bBounds) {
        return Math.abs(aPosition.x - bPosition.x) < aBounds.halfWidth + bBounds.halfWidth
                && Math.abs(aPosition.y - bPosition.y) < aBounds.halfHeight + bBounds.halfHeight;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private LevelStateComponent firstState() {
        return states.size() > 0 ? states.componentAt(0) : null;
    }

    private InputStateComponent firstInput() {
        return inputs.size() > 0 ? inputs.componentAt(0) : null;
    }
}
