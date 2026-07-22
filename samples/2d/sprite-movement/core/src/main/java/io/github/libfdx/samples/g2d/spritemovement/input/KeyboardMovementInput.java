package io.github.libfdx.samples.g2d.spritemovement.input;

import io.github.libfdx.input.Input;
import io.github.libfdx.input.Key;

public final class KeyboardMovementInput implements MovementInput {
    private final Input input;

    public KeyboardMovementInput(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null.");
        }
        this.input = input;
    }

    @Override
    public float horizontal() {
        float value = 0.0f;
        if (input.isKeyPressed(Key.LEFT) || input.isKeyPressed(Key.A)) {
            value -= 1.0f;
        }
        if (input.isKeyPressed(Key.RIGHT) || input.isKeyPressed(Key.D)) {
            value += 1.0f;
        }
        return value;
    }

    @Override
    public float vertical() {
        float value = 0.0f;
        if (input.isKeyPressed(Key.DOWN) || input.isKeyPressed(Key.S)) {
            value -= 1.0f;
        }
        if (input.isKeyPressed(Key.UP) || input.isKeyPressed(Key.W)) {
            value += 1.0f;
        }
        return value;
    }
}
