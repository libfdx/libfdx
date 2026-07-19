package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class Position implements Component {
    public float x;
    public float y;

    public Position(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
