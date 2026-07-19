package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class Player implements Component {
    public boolean onGround = true;
    public boolean facingRight = true;
}
