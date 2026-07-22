package io.github.libfdx.samples.ecs.platformer.input;

import io.github.libfdx.input.Input;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;

public final class BackendPlatformerInput implements PlatformerInput {
    private final Input input;

    public BackendPlatformerInput(Input input) {
        this.input = input;
    }

    @Override
    public boolean leftDown() {
        return input != null && (input.isKeyPressed(Key.A) || input.isKeyPressed(Key.LEFT));
    }

    @Override
    public boolean rightDown() {
        return input != null && (input.isKeyPressed(Key.D) || input.isKeyPressed(Key.RIGHT));
    }

    @Override
    public boolean jumpDown() {
        return input != null
                && (input.isKeyPressed(Key.SPACE)
                || input.isKeyPressed(Key.UP)
                || input.isKeyPressed(Key.W)
                || input.isMouseButtonPressed(MouseButton.LEFT));
    }

    @Override
    public boolean restartDown() {
        return input != null && (input.isKeyPressed(Key.R) || input.isKeyPressed(Key.ENTER) || jumpDown());
    }
}
