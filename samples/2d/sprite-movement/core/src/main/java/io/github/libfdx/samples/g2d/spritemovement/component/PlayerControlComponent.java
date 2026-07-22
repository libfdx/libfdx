package io.github.libfdx.samples.g2d.spritemovement.component;

import io.github.libfdx.ecs.component.Component;

/** Movement speed for a keyboard-controlled entity. */
public final class PlayerControlComponent implements Component {
    public float speed = 3.0f;

    public PlayerControlComponent() {
    }

    public PlayerControlComponent(float speed) {
        this.speed = Math.max(0.0f, speed);
    }
}
