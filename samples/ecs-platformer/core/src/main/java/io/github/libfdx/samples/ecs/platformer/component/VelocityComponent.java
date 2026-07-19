package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class VelocityComponent implements Component {
    public float x;
    public float y;

    public VelocityComponent(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
