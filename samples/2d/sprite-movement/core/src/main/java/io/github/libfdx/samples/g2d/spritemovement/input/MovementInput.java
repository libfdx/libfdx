package io.github.libfdx.samples.g2d.spritemovement.input;

/** Allocation-free movement input consumed by the portable ECS system. */
public interface MovementInput {
    float horizontal();

    float vertical();
}
