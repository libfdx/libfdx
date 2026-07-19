package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class BoundsComponent implements Component {
    public float halfWidth;
    public float halfHeight;

    public BoundsComponent(float halfWidth, float halfHeight) {
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
    }
}
