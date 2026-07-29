package io.github.libfdx.samples.g2d.spritemovement.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.ecs.system.UpdateSystem;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.samples.g2d.spritemovement.component.PlayerControlComponent;
import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;

/** Ordinary libFDX ECS system; it has no engine/editor dependency. */
public final class PlayerControlSystem implements UpdateSystem {
    private static final float DIAGONAL = 0.70710677f;

    private final MovementInput input;
    private World world;
    private ComponentMapper<TransformComponent> transforms;
    private ComponentMapper<PlayerControlComponent> controls;
    private EntityList controlledEntities;
    private boolean enabled = true;

    public PlayerControlSystem(MovementInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null.");
        }
        this.input = input;
    }

    @Override
    public void onAttach(World world) {
        this.world = world;
        transforms = world.mapper(TransformComponent.class);
        controls = world.mapper(PlayerControlComponent.class);
        controlledEntities = world.entities(
                world.matcher().all(TransformComponent.class, PlayerControlComponent.class));
    }

    @Override
    public void onDetach(World world) {
        this.world = null;
        transforms = null;
        controls = null;
        controlledEntities = null;
    }

    @Override
    public void update() {
        float moveX = input.horizontal();
        float moveY = input.vertical();
        if (moveX == 0.0f && moveY == 0.0f) {
            return;
        }
        if (moveX != 0.0f && moveY != 0.0f) {
            moveX *= DIAGONAL;
            moveY *= DIAGONAL;
        }
        float deltaTime = world.deltaTime();
        for (int i = 0; i < controlledEntities.size(); i++) {
            int entity = controlledEntities.entityAt(i);
            Transform transform = transforms.require(entity).transform;
            PlayerControlComponent control = controls.require(entity);
            transform.translate(
                    moveX * control.speed * deltaTime,
                    moveY * control.speed * deltaTime,
                    0.0f);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
