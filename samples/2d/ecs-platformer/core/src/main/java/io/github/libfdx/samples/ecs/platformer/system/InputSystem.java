package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.samples.ecs.platformer.component.InputStateComponent;
import io.github.libfdx.samples.ecs.platformer.input.PlatformerInput;

public final class InputSystem extends BaseGameSystem {
    private final PlatformerInput input;
    private ComponentMapper<InputStateComponent> states;

    public InputSystem(PlatformerInput input) {
        this.input = input;
    }

    @Override
    protected void attach(World world) {
        states = world.mapper(InputStateComponent.class);
    }

    @Override
    public void update() {
        boolean leftDown = input != null && input.leftDown();
        boolean rightDown = input != null && input.rightDown();
        boolean jumpDown = input != null && input.jumpDown();
        boolean restartDown = input != null && input.restartDown();
        for (int i = 0; i < states.size(); i++) {
            states.componentAt(i).update(leftDown, rightDown, jumpDown, restartDown);
        }
    }
}
