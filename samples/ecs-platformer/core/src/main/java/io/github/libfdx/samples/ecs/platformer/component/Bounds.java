package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class Bounds implements Component {
    public float halfWidth;
    public float halfHeight;

    public Bounds(float halfWidth, float halfHeight) {
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
    }
}
