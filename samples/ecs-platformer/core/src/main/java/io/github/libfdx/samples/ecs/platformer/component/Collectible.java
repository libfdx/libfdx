package io.github.libfdx.samples.ecs.platformer.component;

public final class Collectible {
    public final int value;
    public boolean collected;

    public Collectible(int value) {
        this.value = value;
    }
}
