package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class Velocity implements Component {
    public float x;
    public float y;

    public Velocity(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
