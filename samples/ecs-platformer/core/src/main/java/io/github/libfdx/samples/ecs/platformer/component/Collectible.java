package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class Collectible implements Component {
    public final int value;
    public boolean collected;

    public Collectible(int value) {
        this.value = value;
    }
}
